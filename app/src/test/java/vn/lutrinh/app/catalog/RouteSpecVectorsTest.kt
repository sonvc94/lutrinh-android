package vn.lutrinh.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.lutrinh.app.catalog.PackageJson.objects
import vn.lutrinh.app.catalog.PackageJson.strings
import vn.lutrinh.app.domain.catalog.CodeCheck
import vn.lutrinh.app.domain.catalog.CodeProblem
import vn.lutrinh.app.domain.catalog.PackageRules
import vn.lutrinh.app.domain.catalog.RouteCode

/**
 * R40: the app reads codes and packages exactly as the server's shared spec says
 * (spec/codes.json and spec/package-vectors.json of lutrinh-server, copied in test resources).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RouteSpecVectorsTest {
    private val codes = Spec.json("codes.json")

    @Test fun alphabetAndLengthMatchTheSpec() {
        assertEquals(codes.getString("alphabet"), RouteCode.ALPHABET)
        assertEquals(codes.getInt("length"), RouteCode.LENGTH)
    }

    @Test fun sampleCodesAreValid() {
        codes.getJSONArray("samples").strings().forEach { s ->
            assertTrue(s, RouteCode.parse(s) is CodeCheck.Valid)
        }
    }

    @Test fun validInputsNormaliseToTheirCodeAndDisplay() {
        val cases = codes.getJSONArray("valid").objects()
        assertTrue(cases.isNotEmpty())
        cases.forEach { c ->
            val check = RouteCode.parse(c.getString("input"))
            assertTrue(c.toString(), check is CodeCheck.Valid)
            val code = (check as CodeCheck.Valid).code
            assertEquals(c.getString("code"), code.value)
            assertEquals(c.getString("display"), code.display)
        }
    }

    @Test fun invalidInputsGiveTheSpecErrorAndReason() {
        val cases = codes.getJSONArray("invalid").objects()
        assertTrue(cases.isNotEmpty())
        cases.forEach { c ->
            val check = RouteCode.parse(c.getString("input"))
            assertTrue(c.toString(), check is CodeCheck.Invalid)
            val p = (check as CodeCheck.Invalid).problem
            assertEquals(c.toString(), c.getString("error"), p.error)
            assertEquals(c.toString(), c.optString("reason").ifEmpty { null }, p.reason)
        }
    }

    @Test fun everySingleCharacterTypoIsRejectedOnTheDevice() {
        val cases = codes.getJSONObject("singleCharTypos").getJSONArray("cases").objects()
        assertEquals(1624, cases.size)
        cases.forEach { c ->
            assertEquals(c.toString(), CodeCheck.Invalid(CodeProblem.CHECK), RouteCode.parse(c.getString("input")))
        }
    }

    @Test fun adjacentTranspositionsGiveTheSpecResult() {
        val cases = codes.getJSONObject("adjacentTranspositions").getJSONArray("cases").objects()
        assertTrue(cases.isNotEmpty())
        cases.forEach { c ->
            val check = RouteCode.parse(c.getString("input"))
            when (c.getString("result")) {
                "valid" -> assertTrue(c.toString(), check is CodeCheck.Valid)
                else -> assertEquals(c.toString(), CodeCheck.Invalid(CodeProblem.CHECK), check)
            }
        }
    }

    /** Every case of package-vectors.json: 'schema' = not read, 'format_unsupported' = refused first, else the rule codes. */
    @Test fun packageVectorsGiveTheSpecProblems() {
        val vectors = Spec.json("package-vectors.json")
        val base = Spec.json(vectors.getString("base"))
        val cases = vectors.getJSONArray("cases").objects()
        assertTrue(cases.size >= 40)
        cases.forEach { c ->
            val name = c.getString("name")
            val expect = c.getJSONArray("expect").strings().toSet()
            val read = PackageJson.read(Spec.patch(base, c.getJSONArray("ops")).toString())
            when {
                expect == setOf("schema") -> assertTrue("$name: $read", read is PackageRead.Invalid)
                expect == setOf("format_unsupported") -> assertTrue("$name: $read", read is PackageRead.Unsupported)
                else -> {
                    assertTrue("$name: $read", read is PackageRead.Ok)
                    val pkg = (read as PackageRead.Ok).pkg
                    assertEquals(name, expect, PackageRules.check(pkg).toSet())
                    assertEquals(name, emptyList<String>(), PackageRules.appLimits(pkg))
                }
            }
        }
    }
}
