import unittest
import place_names as pn


class PassRule(unittest.TestCase):
    def test_pass_becomes_deo_and_keeps_the_old_name(self):
        self.assertEqual(pn.apply("s", "Mã Pí Lèng Pass", ["Ma Pi Leng Pass"]),
                         ("Đèo Mã Pí Lèng", ["Mã Pí Lèng Pass", "Ma Pi Leng Pass"]))
        self.assertEqual(pn.apply("w", "Mang Yang Pass", []), ("Đèo Mang Yang", ["Mang Yang Pass"]))

    def test_rows_that_would_read_wrong_are_left(self):
        self.assertIsNone(pn.pass_name("m", "Raving Pass"))       # heritage, not a pass
        self.assertIsNone(pn.pass_name("w", "Snake Pass"))        # English word
        self.assertIsNone(pn.pass_name("p", "Pass"))              # nothing before "Pass"
        self.assertIsNone(pn.pass_name("p", "Đèo Gió Pass"))      # already a Đèo
        self.assertIsNone(pn.pass_name("p", "Đèo Mã Phục"))       # no suffix
        self.assertIsNone(pn.pass_name("s", "Passage Cave"))

    def test_decomposed_unicode_is_normalised(self):
        self.assertEqual(pn.pass_name("s", "Mã Pí Lèng Pass"), "Đèo Mã Pí Lèng")


if __name__ == "__main__":
    unittest.main()
