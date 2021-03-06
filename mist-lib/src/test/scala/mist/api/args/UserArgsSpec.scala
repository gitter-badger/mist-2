package mist.api.args

import mist.api.FnContext
import org.scalatest._
import org.scalatest.prop.TableDrivenPropertyChecks._

case class Z(hz: String, yoyo: Int)
case class ComplexCase(abc: Int, hehe: String, x: Boolean, z: Z)
case class Test(a: String, b: Int, z: Boolean, l: Long)

class UserArgsSpec extends FunSpec with Matchers {

  import ArgsInstances._

  describe("complex case class") {


    it("should extract") {
      val params = Map(
        "abc" -> 1,
        "hehe" -> "ads",
        "x" -> false,
        "z" -> Map("hz" -> "asdsad", "yoyo" -> 1)
      )
      val r = arg[ComplexCase].extract(FnContext(params))
      r shouldBe Extracted(ComplexCase(1, "ads", false, Z("asdsad", 1)))
    }

    it("should fail on invalid map") {
      val params = Map(
        "abc" -> 1,
        "hehe" -> "ads",
        "x" -> false,
        "z" -> Map(1 -> "asdsad", 2 -> 1)
      )
      val r = arg[ComplexCase].extract(FnContext(params))
      r.isMissing shouldBe true
    }

    it("should fail on invalid value") {
      val params = Map(
        "abc" -> 1,
        "hehe" -> "ads",
        "x" -> false,
        "z" -> 50
      )
      val r = arg[ComplexCase].extract(FnContext(params))
      r.isMissing shouldBe true
    }

  }

  describe("case class plain") {

    it("should extract") {
      val params = Map(
        "a" -> "A",
        "b" -> 1,
        "z" -> false,
        "l" -> 10L
      )
      val r = arg[Test].extract(FnContext(params))
      r shouldBe Extracted(Test("A", 1, false, 10L))
    }
  }

  describe("basic args") {

    it("named arg") {
      val myArg = arg[Int]("a")
      myArg.extract(FnContext(Map("a" -> 5))) shouldBe Extracted(5)
      myArg.extract(FnContext(Map.empty)).isMissing shouldBe true
    }

    def miss: Missing[Nothing] = Missing("")
    val expected = Table[ArgDef[_], Seq[(String, Any)], ArgExtraction[_]](
      ("arg", "data", "expected"),
      (arg[Boolean]("b"),        Seq("b" -> true), Extracted(true)),
      (arg[Boolean]("b", false), Seq.empty,        Extracted(false)),
      (arg[Boolean]("b"),        Seq.empty,        miss),
      (arg[Option[Boolean]]("b"),Seq("b" -> true), Extracted(Some(true))),
      (arg[Option[Boolean]]("b"),Seq.empty,        Extracted(None)),

      (arg[Int]("n"),         Seq("n" -> 2),  Extracted(2)),
      (arg[Int]("n", 0),      Seq.empty,      Extracted(0)),
      (arg[Int]("n"),         Seq.empty,      miss),
      (arg[Option[Int]]("n"), Seq("n" -> 42), Extracted(Some(42))),
      (arg[Option[Int]]("n"), Seq.empty,      Extracted(None)),

      (arg[String]("s"),          Seq("s" -> "value"), Extracted("value")),
      (arg[String]("s", "value"), Seq.empty,           Extracted("value")),
      (arg[String]("s"),          Seq.empty,           miss),
      (arg[Option[String]]("s"),  Seq("s" -> "yoyo"),  Extracted(Some("yoyo"))),
      (arg[Option[String]]("s"),  Seq.empty,           Extracted(None)),

      (arg[Double]("d"),         Seq("d" -> 2.4),  Extracted(2.4)),
      (arg[Double]("d", 2.2),    Seq.empty,        Extracted(2.2)),
      (arg[Double]("d"),         Seq.empty,        miss),
      (arg[Option[Double]]("d"), Seq("d" -> 42.1), Extracted(Some(42.1))),
      (arg[Option[Double]]("d"), Seq.empty,        Extracted(None)),

      (arg[Seq[Int]]("ints"),       Seq("ints" -> Seq(1,2,3)), Extracted(Seq(1, 2, 3))),

      (arg[Seq[Double]]("doubles"), Seq("doubles" -> Seq(1.1,2.2,3.3)), Extracted(Seq(1.1, 2.2, 3.3))),

      (arg[Seq[Double]]("strings"), Seq("strings" -> Seq("a", "b", "c")), Extracted(Seq("a", "b", "c"))),

      (arg[Seq[Boolean]]("booleans"), Seq("booleans" -> Seq(true, false)), Extracted(Seq(true, false)))
    )

    it("should extract expected result") {
      forAll(expected) { (arg, params, expected) =>
        val ctx = FnContext(params.toMap)
        val result = arg.extract(ctx)
        (expected, result) match {
          case (extr: Extracted[_], res: Extracted[_]) => res shouldBe extr
          case (extr: Missing[_], res: Extracted[_]) => fail(s"for $arg got $res, expected $extr")
          case _ =>
        }
      }
    }
  }

  describe("ArgDef - validate") {

    it("should fail validation on invalid params") {
      val argTest = arg[Int]("test")
      val res = argTest.validate(Map("missing" -> 42))
      res.isLeft shouldBe true
    }

    it("should success validation on valid params") {
      val argTest = arg[Int]("test")
      val res = argTest.validate(Map("test" -> 42))
      res shouldBe Right(())
    }

    it("should skip system arg definition") {
      val res = allArgs.validate(Map.empty)
      res.isRight shouldBe true
    }

    it("should validate .validated rules") {
      val argTest = arg[Int]("test").validated(n => n > 41)
      val res = argTest.validate(Map("test" -> 40))
      res.isLeft shouldBe true
    }
    it("should pass validation on .validates rules") {
      val argTest = arg[Int]("test").validated(n => n > 41)
      val res = argTest.validate(Map("test" -> 42))
      res.isRight shouldBe true
    }
    it("should validate after arg combine") {
      val argTest = arg[Int]("test") & arg[Int]("test2")
      argTest.validate(Map("test" -> 42, "test2" -> 40)).isRight shouldBe true
      argTest.validate(Map("test" -> 42, "missing" -> 0)).isLeft shouldBe true
      argTest.validate(Map("missing" -> 0, "test2" -> 40)).isLeft shouldBe true
      argTest.validate(Map.empty).isLeft shouldBe true
    }
    it("should validate .validated rules after arg combine") {
      val argTest = arg[Int]("test").validated(n => n > 40) & arg[Int]("test2")
      argTest.validate(Map("test"-> 39, "test2" -> 42)).isLeft shouldBe true
    }
  }

  def testCtx(params: (String, Any)*): FnContext = {
    FnContext(params.toMap)
  }
}
