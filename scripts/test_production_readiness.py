import importlib.util
import pathlib
import unittest


class ProductionReadinessTests(unittest.TestCase):
    def test_production_bundle_invariants(self):
        path = pathlib.Path(__file__).with_name("validate_production_readiness.py")
        spec = importlib.util.spec_from_file_location("production_readiness", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.validate()


if __name__ == "__main__":
    unittest.main()
