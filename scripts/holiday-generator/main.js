import { createHash } from "node:crypto";
import { rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { promisify } from "node:util";
import { mkdirSync, existsSync } from "node:fs";

import Holidays from "date-holidays";
import { createEvents as icsCreateEvents } from "ics";

import { COUNTRIES, UNSUPPORTED_COUNTRIES, END_YEAR, FIXED_DATE_START_YEAR, ASSETS_DIR, HOLIDAYS_DIR, SHOULD_LOG, START_YEAR, TYPE_PUBLIC, TYPE_OTHER } from "./config.js";

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
 * Generates reproducible ID for holiday
 * @param {string} name
 * @param {string} date
 * @returns
 */
function generateUid(name, date) {
    const hashGen = createHash("sha1");
    // This helps avoid duplication. For example, "New Year's Day" and "New Years Day" are both the same event.
    const normalizedName = name
        .toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^\p{L}\p{N}]/gu, "");

    const datePart = date.split(" ")[0];
    hashGen.update(`${normalizedName}_${datePart}`);
    return `fossify_${hashGen.digest("hex")}`;
}

/**
 * Get events for country given by code
 * @param {string} countryCode
 * @returns
 */
function getNationalEvents(countryCode) {
    const generator = new Holidays(countryCode);
    generator.setTimezone("UTC");
    const events = {};

    for (let i = START_YEAR; i <= END_YEAR; i++) {
        const holidays = generator.getHolidays(i);
        for (const holiday of holidays) {
            const key = generateUid(holiday.name, holiday.date);
            if (!events[key]) {
                events[key] = holiday;
            }
        }
    }

    return events;
}

/**
 * Get state-specific events for a country.
 * @param {string} countryCode
 * @param {object} nationalEvents Object of national holidays, keyed by UID.
 * @returns {object} Object of state-specific holidays, keyed by UID.
 */
function getStateEvents(countryCode, nationalEvents) {
    const stateEvents = {};
    const generator = new Holidays(countryCode);
    const states = generator.getStates(countryCode);
    if (!states) {
        return {};
    }

    for (const [stateCode, stateName] of Object.entries(states)) {
        generator.init({ country: countryCode, state: stateCode });
        generator.setTimezone("UTC");

        for (let i = START_YEAR; i <= END_YEAR; i++) {
            generator.getHolidays(i).forEach(holiday => {
                if (!TYPE_PUBLIC.includes(holiday.type)) return;
                const key = generateUid(holiday.name, holiday.date);
                if (!nationalEvents[key]) {
                    if (!stateEvents[key]) {
                        stateEvents[key] = { ...holiday, states: [stateName] };
                    } else if (!stateEvents[key].states.includes(stateName)) {
                        stateEvents[key].states.push(stateName);
                    }
                }
            });
        }
    }

    return stateEvents;
}

/**
 * Convert JS Date object to ics.DateTime array
 * @param {Date} date
 * @returns
 */
function getDateArray(date) {
    return [date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate()];
}

/**
 * Checks if holiday is a fixed-date holiday.
 * Regex based on https://github.com/commenthol/date-holidays/blob/master/docs/specification.md#fixed-date
 * @param {object} holiday
 * @returns
 */
function isFixedDate(holiday) {
    return /^\d\d-\d\d( and .*)?$/.test(holiday.rule)
        && !holiday.substitute
        && holiday.date.slice(5, 10) === holiday.rule.slice(0, 5);
}

/**
 * Generate ical file from given set of events
 * @param {object[]} events
 * @returns {Promise<string>}
 */
async function generateIcal(events) {
    const eventsMap = new Map();
    events.forEach((x) => {
        const recurring = isFixedDate(x);
        const start = new Date(`${x.date.slice(0, 10)}T00:00:00Z`);
        if (recurring) start.setUTCFullYear(FIXED_DATE_START_YEAR);
        const startDate = getDateArray(start);
        let uid = generateUid(x.name, recurring ? "" : x.date);
        const existing = eventsMap.get(uid);
        if (recurring && existing && (existing.start[1] !== startDate[1] || existing.start[2] !== startDate[2])) {
            uid = generateUid(x.name, x.date.slice(5, 10));
        }
        if (eventsMap.has(uid)) return;

        const durationDays = Math.ceil((x.end - x.start) / 86400000);
        const end = new Date(start);
        end.setUTCDate(end.getUTCDate() + durationDays);

        eventsMap.set(uid, {
            title: x.name,
            uid,
            start: startDate,
            end: getDateArray(end),
            recurrenceRule: recurring ? "FREQ=YEARLY" : undefined,
            productId: "Fossify Calendar Holiday Generator",
            status: "CONFIRMED",
            description: x.states && x.states.length > 0 ? x.states.join(", ") : "",
        });
    });
    const ical = await createEvents([...eventsMap.values()]);
    return ical;
}

/**
 * Saves content to a file, creating folder if necessary
 * @param {string} content
 * @param {string} folder
 * @param {string} file
 * @returns {Promise<void>}
 */
async function saveFile(content, folder, file) {
    mkdirSync(folder, { recursive: true });
    const filePath = join(folder, file);
    await writeFile(filePath, content, { encoding: "utf-8" });
    log(`File saved to ${filePath}`);
}

/**
 * Generates and saves holiday ics files for each country.
 * @param {object} countries
 * @returns {Promise<void>}
 */
async function saveHolidays(countries) {
    for (const code of Object.keys(countries)) {
        if (!COUNTRIES.includes(code)) continue;

        log(`Generating events for ${code}, ${code}.ics`);
        const nationalEvents = getNationalEvents(code);
        const stateEvents = getStateEvents(code, nationalEvents);

        const eventsByType = {
            public: Object.values(nationalEvents).filter(x => TYPE_PUBLIC.includes(x.type)),
            regional: Object.values(stateEvents),
            other: Object.values(nationalEvents).filter(x => TYPE_OTHER.includes(x.type)),
        };
        const outputDir = join(ASSETS_DIR, HOLIDAYS_DIR, code);
        for (const [type, events] of Object.entries(eventsByType)) {
            if (events.length > 0) {
                await saveFile(await generateIcal(events), outputDir, `${type}.ics`);
            } else {
                await rm(join(outputDir, `${type}.ics`), { force: true });
            }
        }
    }
}

/**
 * Generates and saves metadata.json file containing paths to generated ics files.
 * @param {object} allCountries Object of countries from date-holidays library
 * @returns {Promise<void>}
 */
async function saveMetadata(allCountries) {
    const metadata = [];
    const outputDir = join(ASSETS_DIR, HOLIDAYS_DIR);
    const countryCodes = [...COUNTRIES, ...Object.keys(UNSUPPORTED_COUNTRIES)];
    for (const [code, country] of Object.entries({ ...allCountries, ...UNSUPPORTED_COUNTRIES })) {
        if (!countryCodes.includes(code)) continue;

        const publicPath = `${HOLIDAYS_DIR}/${code}/public.ics`;
        const regionalPath = `${HOLIDAYS_DIR}/${code}/regional.ics`;
        const otherPath = `${HOLIDAYS_DIR}/${code}/other.ics`;

        // not all holiday files are automatically generated, we rely on their existence to determine if they should be included in metadata
        const publicExists = existsSync(join(ASSETS_DIR, publicPath));
        const regionalExists = existsSync(join(ASSETS_DIR, regionalPath));
        const otherExists = existsSync(join(ASSETS_DIR, otherPath));
        if (!publicExists && !regionalExists && !otherExists) continue;
        metadata.push({
            code,
            country,
            public: publicExists ? publicPath : null,
            regional: regionalExists ? regionalPath : null,
            other: otherExists ? otherPath : null,
        });
    }

    const metadataContent = JSON.stringify(metadata, null, 2);
    await saveFile(metadataContent, outputDir, "metadata.json");
}

/**
 * Function generating ical files
 */
async function doWork() {
    const hd = new Holidays();
    const countries = hd.getCountries();
    await saveHolidays(countries);
    await saveMetadata(countries);
}

await doWork();
