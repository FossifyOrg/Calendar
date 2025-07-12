import { cwd } from "node:process";
import { join } from "node:path";

export const SHOULD_LOG = process.env.LOG_ENABLED === "true";

export const ASSETS_DIR = join(cwd(), "../../../app/src/main/assets");
export const HOLIDAYS_DIR = "holidays";

// country codes from https://github.com/commenthol/date-holidays?tab=readme-ov-file#supported-countries-states-regions
export const COUNTRIES = [
    "DZ", // Algeria
    "AR", // Argentina
    "AU", // Australia
    "AT", // Austria
    "BD", // Bangladesh
    "BE", // Belgium
    "BO", // Bolivia
    "BR", // Brazil
    "BG", // Bulgaria
    "CA", // Canada
    "CN", // China
    "CO", // Colombia
    "CR", // Costa Rica
    "HR", // Croatia
    "CZ", // Czech Republic
    "DK", // Denmark
    "EE", // Estonia
    "FI", // Finland
    "FR", // France
    "DE", // Germany
    "GR", // Greece
    "GT", // Guatemala
    "HT", // Haiti
    "HK", // Hong Kong
    "HU", // Hungary
    "IS", // Iceland
    "ID", // Indonesia
    "IE", // Ireland
    "IL", // Israel
    "IT", // Italy
    "JP", // Japan
    "LV", // Latvia
    "LI", // Liechtenstein
    "LT", // Lithuania
    "LU", // Luxembourg
    "MK", // Macedonia
    "MY", // Malaysia
    "MX", // Mexico
    "MA", // Morocco
    "NL", // Netherlands
    "NI", // Nicaragua
    "NG", // Nigeria
    "NO", // Norway
    "PL", // Poland
    "PT", // Portugal
    "RO", // Romania
    "RU", // Russia
    "RS", // Serbia
    "SG", // Singapore
    "SK", // Slovakia
    "SI", // Slovenia
    "ZA", // South Africa
    "KR", // South Korea
    "ES", // Spain
    "SE", // Sweden
    "CH", // Switzerland
    "TW", // Taiwan
    "TH", // Thailand
    "TR", // Turkey
    "UA", // Ukraine
    "GB", // United Kingdom
    "US", // United States
    "UY", // Uruguay
    "VN", // Vietnam
];

export const UNSUPPORTED_COUNTRIES = {
    "IN": "India", // TODO: https://github.com/commenthol/date-holidays/issues/137
    "KZ": "Kazakhstan", // TODO: (no GH issue)
    "PK": "Pakistan", // TODO: https://github.com/commenthol/date-holidays/pull/138
    "LK": "Sri Lanka", // TODO: (no GH issue)
};

export const START_YEAR = new Date().getFullYear(); // start with current year
export const END_YEAR = START_YEAR + 1;
export const FIXED_DATE_START_YEAR = 1980; // start recurring events from start of Unix epoch

// https://www.npmjs.com/package/date-holidays#types-of-holidays
export const TYPE_PUBLIC = ["public", "bank"];
export const TYPE_OTHER = ["optional", "school", "observance"];
