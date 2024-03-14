import { cwd } from "node:process";
import { join } from "node:path";

export const SHOULD_LOG = process.env.LOG_ENABLED === "true";

export const ICS_PATH = join(cwd(), "../../../app/src/main/assets");

// country codes from https://github.com/commenthol/date-holidays?tab=readme-ov-file#supported-countries-states-regions
export const COUNTRIES = [
    ["algeria.ics", "DZ"],
    ["argentina.ics", "AR"],
    ["australia.ics", "AU"],
    ["austria.ics", "AT"],
    ["belgium.ics", "BE"],
    ["bolivia.ics", "BO"],
    ["brazil.ics", "BR"],
    ["bulgaria.ics", "BG"],
    ["canada.ics", "CA"],
    ["china.ics", "CN"],
    ["colombia.ics", "CO"],
    ["costarica.ics", "CR"],
    ["croatia.ics", "HR"],
    ["czech.ics", "CZ"],
    ["denmark.ics", "DK"],
    ["estonia.ics", "EE"],
    ["finland.ics", "FI"],
    ["france.ics", "FR"],
    ["germany.ics", "DE"],
    ["greece.ics", "GR"],
    ["haiti.ics", "HT"],
    ["hungary.ics", "HU"],
    ["iceland.ics", "IS"],
    // TODO add India: https://github.com/commenthol/date-holidays/issues/137
    // ["india.ics", ""],
    ["indonesia.ics", "ID"],
    ["ireland.ics", "IE"],
    ["israel.ics", "IL"],
    ["italy.ics", "IT"],
    ["japan.ics", "jp"],
    // TODO add Kazakhstan: (no GH issue)
    // ["kazakhstan.ics", ""],
    ["latvia.ics", "LV"],
    ["liechtenstein.ics", "LI"],
    ["lithuania.ics", "LT"],
    ["luxembourg.ics", "LU"],
    ["macedonia.ics", "MK"],
    ["malaysia.ics", "MY"],
    ["mexico.ics", "MX"],
    ["morocco.ics", "MA"],
    ["netherlands.ics", "NL"],
    ["nicaragua.ics", "NI"],
    ["nigeria.ics", "NG"],
    ["norway.ics", "NO"],
    // TODO add Pakistan: https://github.com/commenthol/date-holidays/pull/138
    // ["pakistan.ics", ""],
    ["poland.ics", "PL"],
    ["portugal.ics", "PT"],
    ["romania.ics", "RO"],
    ["russia.ics", "RU"],
    ["serbia.ics", "RS"],
    ["singapore.ics", "SG"],
    ["slovakia.ics", "SK"],
    ["slovenia.ics", "SI"],
    ["southafrica.ics", "ZA"],
    ["southkorea.ics", "KR"],
    ["spain.ics", "ES"],
    // TODO add Sri Lanka: (no GH issue)
    // ["srilanka.ics", ""],
    ["sweden.ics", "SE"],
    ["switzerland.ics", "CH"],
    ["taiwan.ics", "TW"],
    ["thailand.ics", "TH"],
    ["turkey.ics", "TR"],
    ["ukraine.ics", "UA"],
    ["unitedkingdom.ics", "GB"],
    ["unitedstates.ics", "US"],
    ["uruguay.ics", "UY"],
];

export const START_YEAR = new Date().getFullYear(); // start with current year
export const END_YEAR = START_YEAR + 1;
export const FIXED_DATE_START_YEAR = 1970; // start recurring events from start of Unix epoch

// https://www.npmjs.com/package/date-holidays#types-of-holidays
export const TYPE_WHITELIST = ["public", "bank"];
