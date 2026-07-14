# CHANGELOG
All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). See the [CONTRIBUTING guide](./CONTRIBUTING.md#Changelog) for instructions on how to add changelog entries.

## [v5.0.0]
### Added
- New quality check workflows [(#737)](https://github.com/wazuh/wazuh-indexer/issues/737)
- Add repository bumper [(#43)](https://github.com/wazuh/wazuh-indexer-plugins/issues/43)
- Add scripts to check the opensearch and product version [(#999)](https://github.com/wazuh/wazuh-indexer/issues/999)
- Add documentation to bring up an SMTP server for development [(#48)](https://github.com/wazuh/wazuh-indexer-plugins/issues/48)
- Add support for multiple notification channels [(#65)](https://github.com/wazuh/wazuh-indexer-reporting/issues/65)
- Implement delivery of notifications on reports creation [(#62)](https://github.com/wazuh/wazuh-indexer-reporting/issues/62)
- Add `--set-as-main` flag support to repository bumper [(#136)](https://github.com/wazuh/wazuh-indexer-reporting/issues/136)
- Add revert logic to bumper workflow [(#153)](https://github.com/wazuh/wazuh-indexer-reporting/issues/153)
- Add limit for report definitions [(#1276)](https://github.com/wazuh/wazuh-indexer-plugins/issues/1276)

### Dependencies

### Changed
- Replace third-party Action to check commiter's email with our forked version [(#70)](https://github.com/wazuh/wazuh-indexer-reporting/issues/70)
- Add version to the GH Workflow names [(#73)](https://github.com/wazuh/wazuh-indexer-plugins/issues/73)
- Update GitHub Actions versions in main branch [(#1129)](https://github.com/wazuh/wazuh-indexer/issues/1129)
- Check GitHub actions with dependabot [(#86)](https://github.com/wazuh/wazuh-indexer-plugins/issues/86)

### Deprecated

### Removed

### Fixed
- Fix broken links [(#75)](https://github.com/wazuh/wazuh-indexer-reporting/issues/75)
- Fix CodeQL workflow [(#74)](https://github.com/wazuh/wazuh-indexer-reporting/issues/74)
- Fix link-checker workflow [(#867)](https://github.com/wazuh/wazuh-indexer-plugins/issues/867)
- Fix maven cache in CodeQL workflow [(#141)](https://github.com/wazuh/wazuh-indexer-reporting/issues/141)

### Security

## Prior versions
- []()

[Unreleased 5.0.x]: https://github.com/wazuh/wazuh-indexer-reporting/compare/52180e3e4f78a7ac049d96d0bb06f51ec009638b...5.0.0
