# CHANGELOG
All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). See the [CONTRIBUTING guide](./CONTRIBUTING.md#Changelog) for instructions on how to add changelog entries.

## [Unreleased 5.0.x]
### Added
- Test entry for automated merge test [5538]
- New quality check workflows [(#30)](https://github.com/wazuh/wazuh-indexer-reporting/pull/30)
- Add repository bumper [(#43)](https://github.com/wazuh/wazuh-indexer-plugins/pull/43)
- Add scripts to check the opensearch and product version [(#59)](https://github.com/wazuh/wazuh-indexer-reporting/pull/59)
- Add documentation to bring up an SMTP server for development [(#60)](https://github.com/wazuh/wazuh-indexer-plugins/pull/60)
- Add support for multiple notification channels [(#66)](https://github.com/wazuh/wazuh-indexer-reporting/pull/66)
- Implement delivery of notifications on reports creation [(#67)](https://github.com/wazuh/wazuh-indexer-reporting/pull/67)
- Add `--set-as-main` flag support to repository bumper [(#139)](https://github.com/wazuh/wazuh-indexer-reporting/pull/139)

### Dependencies

### Changed
- Replace third-party Action to check commiter's email with our forked version [(#71)](https://github.com/wazuh/wazuh-indexer-reporting/pull/71)
- Add version to the GH Workflow names [(#73)](https://github.com/wazuh/wazuh-indexer-plugins/pull/73)
- Update GitHub Actions versions in main branch [(#78)](https://github.com/wazuh/wazuh-indexer-reporting/pull/78)
- Check GitHub actions with dependabot [(#86)](https://github.com/wazuh/wazuh-indexer-plugins/pull/86)

### Deprecated

### Removed

### Fixed
- Fix broken links [(#77)](https://github.com/wazuh/wazuh-indexer-reporting/pull/77)
- Fix CodeQL workflow [(#76)](https://github.com/wazuh/wazuh-indexer-reporting/pull/76)
- Fix link-checker workflow [(#121)](https://github.com/wazuh/wazuh-indexer-reporting/pull/121)
- Fix maven cache in CodeQL workflow [(#142)](https://github.com/wazuh/wazuh-indexer-reporting/pull/142)

### Security

[Unreleased 5.0.x]: https://github.com/wazuh/wazuh-indexer-reporting/compare/7032eef2cd847b3dc4e805adaca2e6c0a516f05a...main
