import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location(
    "validate_publication", Path(__file__).resolve().parents[1] / "scripts/validate_publication.py"
)
publication = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publication)


class PublicationConfigurationTest(unittest.TestCase):
    def configuration(self):
        return {name: "https://example.invalid" for name in publication.REQUIRED_ORIGINS}

    def test_both_portals_configured(self):
        publication.validate_configuration(self.configuration())

    def test_missing_effiplan_blocks_publication(self):
        config = self.configuration()
        del config["EFFIPLAN_ORIGIN"]
        with self.assertRaisesRegex(ValueError, "EFFIPLAN_ORIGIN"):
            publication.validate_configuration(config)

    def test_each_origin_is_required(self):
        for name in publication.REQUIRED_ORIGINS:
            for value in ("", " ", " https://example.invalid", "https://example.invalid "):
                with self.subTest(name=name, value=value):
                    config = self.configuration()
                    config[name] = value
                    with self.assertRaisesRegex(ValueError, name):
                        publication.validate_configuration(config)

    def test_rejects_non_origins_without_logging_values(self):
        for value in (
            "http://example.invalid", "https://example.invalid/login",
            "https://user:private@example.invalid", "https://example.invalid?private=value",
            "https://example.invalid#private", "https://example.invalid:bad",
            "https://", "https://example.invalid?", "https://example.invalid#",
        ):
            with self.subTest(value=value):
                config = self.configuration()
                config["EFFIPLAN_ORIGIN"] = value
                with self.assertRaises(ValueError) as error:
                    publication.validate_configuration(config)
                self.assertNotIn(value, str(error.exception))
                self.assertIn("EFFIPLAN_ORIGIN", str(error.exception))

    def test_optional_trailing_slash(self):
        config = self.configuration()
        config["EFFIPLAN_ORIGIN"] += "/"
        publication.validate_configuration(config)


if __name__ == "__main__":
    unittest.main()
