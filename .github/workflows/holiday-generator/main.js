import { createHash } from "node:crypto";
import { writeFile } from "node:fs/promises";
import { join } from "node:path";
import { promisify } from "node:util";

import Holidays from "date-holidays";
import { createEvents as icsCreateEvents } from "ics";

import { COUNTRIES, END_YEAR, FIXED_DATE_START_YEAR, ICS_PATH, SHOULD_LOG, START_YEAR, TYPE_WHITELIST } from "./config.js";

// converting createEvents from ics from function with callback to async function for easier usage
const createEvents = promisify(icsCreateEvents);

/**
 * Log info to console
 * @param {*} toLog
 */
function log(toLog) {
    if (SHOULD_LOG) {
        console.log(toLog);
    }
}

/**
 * Get events for country given by code
 * @param {string} countryCode
 * @returns
 */
function getEvents(countryCode) {
    const generator = new Holidays(countryCode);
    const events = [];
    for (let i = START_YEAR; i <= END_YEAR; i++) {
        events.push(...generator.getHolidays(i).filter((x) => TYPE_WHITELIST.includes(x.type)));
    }
    return events;
}

/**
 * Generates reproducible ID for holiday
 * @param {string} countryCode
 * @param {string} date
 * @param {string} rule
 * @returns
 */
function generateUid(countryCode, date, rule) {
    const hashGen = createHash("sha256");
    hashGen.update(`${countryCode},${date},${rule}`);
    return hashGen.digest("hex");
}

/**
 * Convert JS Date object to ics.DateTime array
 * @param {Date} date
 * @returns
 */
function getDateArray(date) {
    return [date.getFullYear(), date.getMonth() + 1, date.getDate()];
}

/**
 * Checks if holiday is a fixed-date holiday.
 * Regex based on https://github.com/commenthol/date-holidays/blob/master/docs/specification.md#fixed-date
 * @param {string} rule
 * @returns
 */
function isFixedDate(rule) {
    return /^\d\d-\d\d( and .*)?$/.test(rule);
}

/**
 * Generate ical file from given set of events
 * @param {ReturnType<getEvents>} events
 * @param {string} countryCode
 * @returns {Promise<string>}
 */
async function generateIcal(events, countryCode) {
    const eventsMap = new Map();
    events.forEach((x) => {
        if (isFixedDate(x.rule)) {
            const uid = generateUid(countryCode, "", x.rule);
            if (!eventsMap.has(uid)) {
                const yearDiff = x.end.getFullYear() - x.start.getFullYear();
                x.start.setFullYear(FIXED_DATE_START_YEAR);
                x.end.setFullYear(FIXED_DATE_START_YEAR + yearDiff);
                eventsMap.set(uid, {
                    title: x.name,
                    uid,
                    start: getDateArray(x.start),
                    end: getDateArray(x.end),
                    recurrenceRule: "FREQ=YEARLY",
                    productId: "Fossify Calendar Holiday Generator",
                    status: "CONFIRMED",
                });
            }
        } else {
            const uid = generateUid(countryCode, x.date, x.rule);
            eventsMap.set(uid, {
                title: x.name,
                uid,
                start: getDateArray(x.start),
                end: getDateArray(x.end),
                productId: "Fossify Calendar Holiday Generator",
                status: "CONFIRMED",
            });
        }
    });
    const ical = await createEvents([...eventsMap.values()]);
    return ical;
}

/**
 * Function generating ical files
 */
async function doWork() {
    for (const [file, code] of COUNTRIES) {
        log(`Generating events for ${code}, ${file}`);
        const events = getEvents(code);
        const ical = await generateIcal(events, code);
        const filePath = join(ICS_PATH, file);
        await writeFile(filePath, ical, { encoding: "utf-8" });
        log(`File saved to ${filePath}`);
    }
}

await doWork();
