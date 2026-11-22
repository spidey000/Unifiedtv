"""Regression checks for release version selection and certificate continuity."""
import unittest
from unittest.mock import patch

from tools import unifiedtv_release as release


class ReleasePolicyTests(unittest.TestCase):
    def test_semver_order_and_canonical_tags(self):
        self.assertEqual(max(["v0.1.9", "v0.1.10", "v0.0.99"], key=release.semver), "v0.1.10")
        for tag in ("v0.1.2-rc.1", "v0.01.0", "0.1.2", "v0.1"):
            self.assertIsNone(release.semver(tag))

    def test_patch_default_and_highest_bump_across_commits(self):
        version = (0, 1, 2)
        self.assertEqual(release.next_tag(version, ["Fix: release readiness"]), "v0.1.3")
        self.assertEqual(release.next_tag(version, ["feat(tv): new shelf", "fix: minor bug"]), "v0.2.0")
        self.assertEqual(release.next_tag(version, ["feat: a shelf", "fix(tv)!: change contract"]), "v1.0.0")
        self.assertEqual(release.next_tag(version, ["refactor: shelf\n\nBREAKING CHANGE: removes old option"]), "v1.0.0")

    def test_android_code_always_increments_from_previous_apk(self):
        self.assertEqual(release.next_code(None), 11000)
        self.assertEqual(release.next_code(11000), 11001)
        self.assertEqual(release.next_code(48023), 48024)
        for invalid in (0, -1, release.MAX_CODE):
            with self.subTest(code=invalid), self.assertRaises(SystemExit):
                release.next_code(invalid)

    @patch.object(release, "command")
    def test_reject_signing_key_changes_extra_signers_and_debug(self, command):
        digest = "a" * 64
        cert = f"Signer #1 certificate SHA-256 digest: {digest}\n"
        command.return_value = "Signer #1 certificate DN: CN=UnifiedTV\n" + cert
        release.verify_signer("apksigner", "release.apk", digest)
        with self.assertRaises(SystemExit):
            release.verify_signer("apksigner", "previous.apk", "b" * 64)
        command.return_value += f"Signer #2 certificate SHA-256 digest: {digest}\n"
        with self.assertRaises(SystemExit):
            release.verify_signer("apksigner", "release.apk", digest)
        command.return_value = "Signer #1 certificate DN: C=US, CN=Android Debug\n" + cert
        with self.assertRaises(SystemExit):
            release.verify_signer("apksigner", "release.apk", digest)


if __name__ == "__main__":
    unittest.main()
