import { cwd } from "node:process";
import { join } from "node:path";

export const SHOULD_LOG = process.env.LOG_ENABLED === "true";

export const ICS_PATH = join(cwd(), "../../../app/src/main/assets");

// country codes from https://github.com/commenthol/date-holidays?tab=readme-ov-file#supported-countries-states-regions
export const COUNTRIES = [
    { file: "algeria.ics", code: "DZ" },
    { file: "argentina.ics", code: "AR" },
    { file: "australiancapitalterritory.ics", code: "AU", state: "ACT" },
    { file: "austria.ics", code: "AT" },
    { file: "belgium.ics", code: "BE" },
    { file: "bolivia.ics", code: "BO" },
    { file: "brazil.ics", code: "BR" },
    { file: "bulgaria.ics", code: "BG" },
    { file: "canada.ics", code: "CA" },
    { file: "china.ics", code: "CN" },
    { file: "colombia.ics", code: "CO" },
    { file: "costarica.ics", code: "CR" },
    { file: "croatia.ics", code: "HR" },
    { file: "czech.ics", code: "CZ" },
    { file: "denmark.ics", code: "DK" },
    { file: "estonia.ics", code: "EE" },
    { file: "finland.ics", code: "FI" },
    { file: "france.ics", code: "FR" },
    { file: "germany.ics", code: "DE" },
    { file: "greece.ics", code: "GR" },
    { file: "haiti.ics", code: "HT" },
    { file: "hungary.ics", code: "HU" },
    { file: "iceland.ics", code: "IS" },
    // TODO add India: https://github.com/commenthol/date-holidays/issues/137
    // {file: "india.ics", code:"IN"},
    { file: "indonesia.ics", code: "ID" },
    { file: "ireland.ics", code: "IE" },
    { file: "israel.ics", code: "IL" },
    { file: "italy.ics", code: "IT" },
    { file: "japan.ics", code: "jp" },
    // TODO add Kazakhstan: (no GH issue)
    // {file: "kazakhstan.ics", code:"KZ"},
    { file: "latvia.ics", code: "LV" },
    { file: "liechtenstein.ics", code: "LI" },
    { file: "lithuania.ics", code: "LT" },
    { file: "luxembourg.ics", code: "LU" },
    { file: "macedonia.ics", code: "MK" },
    { file: "malaysia.ics", code: "MY" },
    { file: "mexico.ics", code: "MX" },
    { file: "morocco.ics", code: "MA" },
    { file: "netherlands.ics", code: "NL" },
    { file: "newsouthwales.ics", code: "AU", state: "NSW" },
    { file: "nicaragua.ics", code: "NI" },
    { file: "nigeria.ics", code: "NG" },
    { file: "northernterritory.ics", code: "AU", state: "NT" },
    { file: "norway.ics", code: "NO" },
    // TODO add Pakistan: https://github.com/commenthol/date-holidays/pull/138
    // {file: "pakistan.ics", code: "PK"},
    { file: "poland.ics", code: "PL" },
    { file: "portugal.ics", code: "PT" },
    { file: "queensland.ics", code: "AU", state: "QLD" },
    { file: "romania.ics", code: "RO" },
    { file: "russia.ics", code: "RU" },
    { file: "serbia.ics", code: "RS" },
    { file: "singapore.ics", code: "SG" },
    { file: "slovakia.ics", code: "SK" },
    { file: "slovenia.ics", code: "SI" },
    { file: "southafrica.ics", code: "ZA" },
    { file: "southaustralia.ics", code: "AU", state: "SA" },
    { file: "southkorea.ics", code: "KR" },
    { file: "spain.ics", code: "ES" },
    // TODO add Sri Lanka: (no GH issue)
    // { file: "srilanka.ics", code: "LK" },
    { file: "sweden.ics", code: "SE" },
    { file: "switzerland.ics", code: "CH" },
    { file: "taiwan.ics", code: "TW" },
    { file: "tasmania.ics", code: "AU", state: "TAS" },
    { file: "thailand.ics", code: "TH" },
    { file: "turkey.ics", code: "TR" },
    { file: "ukraine.ics", code: "UA" },
    { file: "unitedkingdom.ics", code: "GB" },
    { file: "unitedstates.ics", code: "US" },
    { file: "uruguay.ics", code: "UY" },
    { file: "victoria.ics", code: "AU", state: "VIC" },
    { file: "westernaustralia.ics", code: "AU", state: "WA" },
];

export const START_YEAR = new Date().getFullYear(); // start with current year
export const END_YEAR = START_YEAR + 1;
export const FIXED_DATE_START_YEAR = 1970; // start recurring events from start of Unix epoch

// https://www.npmjs.com/package/date-holidays#types-of-holidays
export const TYPE_WHITELIST = ["public", "bank"];
