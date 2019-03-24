package user.org.mockito

import org.mockito.captor.ArgCaptor
import org.mockito.exceptions.verification.ArgumentsAreDifferent
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.{ CallsRealMethods, DefaultAnswer, ScalaFirstStubbing }
import org.mockito.{ ArgumentMatchersSugar, MockitoSugar }
import org.scalactic.Prettifier
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{ EitherValues, Matchers, OptionValues, WordSpec }
import user.org.mockito.matchers.ValueCaseClass

//noinspection RedundantDefaultArgument
class MockitoSugarTest
    extends WordSpec
    with MockitoSugar
    with Matchers
    with ArgumentMatchersSugar
    with EitherValues
    with OptionValues
    with TableDrivenPropertyChecks {

  implicit val prettifier: Prettifier = new Prettifier {
    override def apply(o: Any): String = o match {
      case Baz2(_, s) => s"PrettifiedBaz($s)"
      case other      => Prettifier.default(other)
    }
  }

  val scenarios = Table(
    ("testDouble", "foo", "higherKinded", "concreteHigherKinded", "fooWithBaz", "baz", "parametrisedTraitInt"),
    ("mock",
     () => mock[Foo],
     () => mock[HigherKinded[Option]],
     () => mock[ConcreteHigherKinded],
     () => mock[FooWithBaz],
     () => mock[Baz],
     () => mock[ParametrisedTraitInt]),
    ("spy",
     () => spy(new Foo),
     () => spy(new HigherKinded[Option]),
     () => spy(new ConcreteHigherKinded),
     () => spy(new FooWithBaz),
     () => spy(new ConcreteBaz),
     () => spy(new ParametrisedTraitInt))
  )

  forAll(scenarios) { (testDouble, foo, higherKinded, concreteHigherKinded, fooWithBaz, baz, parametrisedTraitInt) =>
    testDouble should {
      "deal with stubbing value type parameters" in {
        val aMock = parametrisedTraitInt()

        when(aMock.m()) thenReturn 100

        aMock.m() shouldBe 100

        verify(aMock).m()
      }

      "deal with verifying value type parameters" in {
        val aMock = parametrisedTraitInt()
        //this has to be done separately as the verification in the other test would return the stubbed value so the
        // null problem on the primitive would not happen
        verify(aMock, never).m()
      }

      "create a valid mock" in {
        val aMock = foo()

        when(aMock.bar) thenReturn "mocked!"

        aMock.bar shouldBe "mocked!"
      }

      "stub a value class return value" in {
        val aMock = foo()

        when(aMock.returnsValueCaseClass) thenReturn ValueCaseClass(100) andThen ValueCaseClass(200)

        aMock.returnsValueCaseClass shouldBe ValueCaseClass(100)
        aMock.returnsValueCaseClass shouldBe ValueCaseClass(200)
      }

      "create a mock with nice answer API (multiple params)" in {
        val aMock = foo()

        when(aMock.doSomethingWithThisIntAndString(*, *)) thenAnswer ((i: Int, s: String) => ValueCaseClass(i * 10 + s.toInt)) andThenAnswer (
            (i: Int,
             _: String) => ValueCaseClass(i))

        aMock.doSomethingWithThisIntAndString(4, "2") shouldBe ValueCaseClass(42)
        aMock.doSomethingWithThisIntAndString(4, "2") shouldBe ValueCaseClass(4)
      }

      "create a mock while stubbing another" in {
        val aMock = foo()

        when(aMock.returnBar) thenReturn mock[Bar]

        aMock.returnBar shouldBe a[Bar]
      }

      "default answer should deal with default arguments" in {
        val aMock = foo()

        aMock.iHaveSomeDefaultArguments("I'm not gonna pass the second argument")
        aMock.iHaveSomeDefaultArguments("I'm gonna pass the second argument", "second argument")

        verify(aMock).iHaveSomeDefaultArguments("I'm not gonna pass the second argument", "default value")
        verify(aMock).iHaveSomeDefaultArguments("I'm gonna pass the second argument", "second argument")
      }

      "work with by-name arguments (argument order doesn't matter when not using matchers)" in {
        val aMock = foo()

        when(aMock.iStartWithByNameArgs("arg1", "arg2")) thenReturn "mocked!"

        aMock.iStartWithByNameArgs("arg1", "arg2") shouldBe "mocked!"
        aMock.iStartWithByNameArgs("arg111", "arg2") should not be "mocked!"

        verify(aMock).iStartWithByNameArgs("arg1", "arg2")
        verify(aMock).iStartWithByNameArgs("arg111", "arg2")
      }

      "work with primitive by-name arguments" in {
        val aMock = foo()

        when(aMock.iHavePrimitiveByNameArgs(1, "arg2")) thenReturn "mocked!"

        aMock.iHavePrimitiveByNameArgs(1, "arg2") shouldBe "mocked!"
        aMock.iHavePrimitiveByNameArgs(2, "arg2") should not be "mocked!"

        verify(aMock).iHavePrimitiveByNameArgs(1, "arg2")
        verify(aMock).iHavePrimitiveByNameArgs(2, "arg2")
      }

      "work with Function0 arguments" in {
        val aMock = foo()

        when(aMock.iHaveFunction0Args(eqTo("arg1"), function0("arg2"))) thenReturn "mocked!"

        aMock.iHaveFunction0Args("arg1", () => "arg2") shouldBe "mocked!"
        aMock.iHaveFunction0Args("arg1", () => "arg3") should not be "mocked!"

        verify(aMock).iHaveFunction0Args(eqTo("arg1"), function0("arg2"))
        verify(aMock).iHaveFunction0Args(eqTo("arg1"), function0("arg3"))
      }

      "reset" in {
        val aMock = foo()

        when(aMock.bar) thenReturn "mocked!"
        when(aMock.iHavePrimitiveByNameArgs(1, "arg2")) thenReturn "mocked!"

        aMock.bar shouldBe "mocked!"
        aMock.iHavePrimitiveByNameArgs(1, "arg2") shouldBe "mocked!"

        reset(aMock)

        aMock.bar should not be "mocked!"
        aMock.iHavePrimitiveByNameArgs(1, "arg2") should not be "mocked!"

        //to verify the reset mock handler still handles by-name params
        when(aMock.iHavePrimitiveByNameArgs(1, "arg2")) thenReturn "mocked!"

        aMock.iHavePrimitiveByNameArgs(1, "arg2") shouldBe "mocked!"
      }

      "ignore the calls to the methods that provide default arguments" in {
        val aMock = foo()

        aMock.iHaveSomeDefaultArguments("I'm not gonna pass the second argument")

        verify(aMock).iHaveSomeDefaultArguments("I'm not gonna pass the second argument", "default value")
        verifyNoMoreInteractions(aMock)
      }

      "support an ArgCaptor and deal with default arguments" in {
        val aMock = foo()

        aMock.iHaveSomeDefaultArguments("I'm not gonna pass the second argument")

        val captor1 = ArgCaptor[String]
        val captor2 = ArgCaptor[String]
        verify(aMock).iHaveSomeDefaultArguments(captor1, captor2)

        captor1 hasCaptured "I'm not gonna pass the second argument"
        captor2 hasCaptured "default value"
      }

      "work with parametrised return types" in {
        val aMock = higherKinded()

        when(aMock.method) thenReturn Some(Right("Mocked!"))

        aMock.method.value.right.value shouldBe "Mocked!"
        aMock.method2
      }

      "work with parametrised return types declared in parents" in {
        val aMock = concreteHigherKinded()

        when(aMock.method) thenReturn Some(Right("Mocked!"))

        aMock.method.value.right.value shouldBe "Mocked!"
        aMock.method2
      }

      "work with higher kinded types and auxiliary methods" in {
        def whenGetById[F[_]](algebra: HigherKinded[F]): ScalaFirstStubbing[F[Either[String, String]]] =
          when(algebra.method)

        val aMock = higherKinded()

        whenGetById(aMock) thenReturn Some(Right("Mocked!"))

        aMock.method.value.right.value shouldBe "Mocked!"
      }

      "work with standard mixins" in {
        val aMock = fooWithBaz()

        when(aMock.bar) thenReturn "mocked!"
        when(aMock.traitMethod(any)) thenReturn ValueCaseClass(69)

        aMock.bar shouldBe "mocked!"
        aMock.traitMethod(30) shouldBe ValueCaseClass(69)

        verify(aMock).traitMethod(30)
      }

      "create a mock with nice answer API (single param)" in {
        val aMock = baz()

        when(aMock.traitMethod(*)) thenAnswer ((i: Int) => ValueCaseClass(i * 10 + 2)) andThenAnswer ((i: Int) =>
          ValueCaseClass(i * 10 + 3))

        aMock.traitMethod(4) shouldBe ValueCaseClass(42)
        aMock.traitMethod(4) shouldBe ValueCaseClass(43)
      }

      "create a mock with nice answer API (invocation usage)" in {
        val aMock = baz()

        when(aMock.traitMethod(*)) thenAnswer ((i: InvocationOnMock) => ValueCaseClass(i.getArgument[Int](0) * 10 + 2)) andThenAnswer (
            (i: InvocationOnMock) => ValueCaseClass(i.getArgument[Int](0) * 10 + 3))

        aMock.traitMethod(4) shouldBe ValueCaseClass(42)
        aMock.traitMethod(4) shouldBe ValueCaseClass(43)
      }

      "use Prettifier for the arguments" in {
        val aMock = foo()

        aMock.baz(42, Baz2(69, "hola"))

        val e = the[ArgumentsAreDifferent] thrownBy {
          verify(aMock).baz(42, Baz2(69, "chau"))
        }

        e.getMessage should include("Argument(s) are different! Wanted:")
        e.getMessage should include("foo.baz(42, PrettifiedBaz(hola));")
        e.getMessage should include("Actual invocation has different arguments:")
        e.getMessage should include("foo.baz(42, PrettifiedBaz(chau));")
      }
    }
  }

  "mock[T]" should {
    "work with type aliases" in {
      type MyType = String

      val aMock = mock[MyType => String]

      when(aMock("Hola")) thenReturn "Chau"

      aMock("Hola") shouldBe "Chau"
    }

    "create a mock with default answer" in {
      val aMock = mock[Foo](CallsRealMethods)

      aMock.bar shouldBe "not mocked"
    }

    "create a mock with default answer from implicit scope" in {
      implicit val defaultAnswer: DefaultAnswer = CallsRealMethods

      val aMock = mock[Foo]

      aMock.bar shouldBe "not mocked"
    }

    "create a mock with name" in {
      val aMock = mock[Foo]("Nice Mock")

      aMock.toString shouldBe "Nice Mock"
    }

    "work with inline mixins" in {
      val aMock = mock[Foo with Baz]

      when(aMock.bar) thenReturn "mocked!"
      when(aMock.traitMethod(any)) thenReturn ValueCaseClass(69)

      aMock.bar shouldBe "mocked!"
      aMock.traitMethod(30) shouldBe ValueCaseClass(69)

      verify(aMock).traitMethod(30)
    }

    "stop the user passing traits in the settings" in {
      a[IllegalArgumentException] should be thrownBy {
        mock[Foo](withSettings.extraInterfaces(classOf[Baz]))
      }
    }
  }

  "spyLambda[T]" should {
    "create a valid spy for lambdas and anonymous classes" in {
      val aSpy = spyLambda((arg: String) => s"Got: $arg")

      when(aSpy.apply(any)) thenReturn "mocked!"

      aSpy("hi!") shouldBe "mocked!"
      verify(aSpy).apply("hi!")
    }
  }
}
