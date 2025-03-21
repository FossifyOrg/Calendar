### Reporting
Before you report something, read the reporting rules [here](https://github.com/FossifyOrg/General-Discussion#how-do-i-suggest-an-improvement-ask-a-question-or-report-an-issue) please.

### Contributing as a developer
Some instructions about code style and everything that has to be done to increase the chance of your code getting accepted can be found at the [General Discussion](https://github.com/FossifyOrg/General-Discussion#contribution-rules-for-developers) section.

### Contributing as a non developer
In case you just want to for example improve a translation, you can find the way of doing it [here](https://github.com/FossifyOrg/General-Discussion#how-can-i-suggest-an-edit-to-a-file).

### Contributing holidays

- For most countries, holidays are generated automatically using the [date-holidays](https://github.com/commenthol/date-holidays) library. You can find the list of countries supported by date-holidays [here](https://github.com/commenthol/date-holidays?tab=readme-ov-file#supported-countries-states-regions).
- The app includes **a different set of countries** compared to date-holidays. For an exhaustive list of countries **_included_ in the app**, check out [holiday generator configuration](https://github.com/FossifyOrg/Calendar/blob/master/.github/workflows/holiday-generator/config.js) file.
- Contributions are welcome for countries that are not yet supported by date-holidays but _are_ included in the app.

#### Adding holidays

_Basic knowledge about git, forks, pull requests and [ICS syntax](https://en.wikipedia.org/wiki/ICalendar) is required._

Adding holidays manually is slightly complicated than necessary due to periodic updates from the generator (to be simplified) so it's highly recommended to add holidays directly to the upstream date-holidays project. That way, everyone benefits from your contribution but if that's not an option, follow these steps:

- Create a new folder in the [holidays directory](https://github.com/FossifyOrg/Calendar/tree/master/app/src/main/assets/holidays). The folder name should be your country's [ISO 3166-2](https://en.wikipedia.org/wiki/ISO_3166-2) code.
- Create the following ICS files inside your newly created folder:
  - `public.ics` for public holidays.
  - (optional) `regional.ics` for regional/state-specific holidays (holidays observed in some state/region but not country-wide).
  - (optional) `other.ics` for observances and other non-official holidays.
- Update [metadata.json](https://github.com/FossifyOrg/Calendar/blob/master/app/src/main/assets/holidays/metadata.json) as per your country code and the corresponding ICS files you have created.
- Add your country to the list _unsupported_ countries in the [holiday generator configuration](https://github.com/FossifyOrg/Calendar/blob/master/.github/workflows/holiday-generator/config.js#L73) file. This step ensures your holiday files aren't removed by the generator's future updates.
- Test your changes locally to ensure everything is working as expected.
- Finally, commit your changes and open a pull request.
