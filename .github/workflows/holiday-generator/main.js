import { createHash } from "node:crypto";
import { writeFile } from "node:fs/promises";
import { join } from "node:path";
import { promisify } from "node:util";

import Holidays from "date-holidays";
import { createEvents as icsCreateEvents } from "ics";

import { COUNTRIES, END_YEAR, ICS_PATH, SHOULD_LOG, START_YEAR, TYPE_WHITELIST } from "./config.js";

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
 * @returns
 */
function generateUid(countryCode, date) {
    const hashGen = createHash("sha256");
    hashGen.update(`${countryCode},${date}`);
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
 * Generate ical file from given set of events
 * @param {ReturnType<getEvents>} events
 * @param {string} countryCode
 * @returns {Promise<string>}
 */
async function generateIcal(events, countryCode) {
    const ical = await createEvents(
        events.map((x) => ({
            title: x.name,
            uid: generateUid(countryCode, x.date),
            start: getDateArray(x.start),
            end: getDateArray(x.end),
            productId: "Fossify Calendar Holiday Generator",
            status: "CONFIRMED",
        }))
    );
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
