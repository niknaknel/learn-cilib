package example

object OptionExample extends App {

  /**
   * Imagine we wanted to access the head element of a list.
   * The method below is unsafe as it might throw an exception.
   * Alternatively, we could use a default value.
   * Ether approach requires that the developer calling this function to handle the
   * exception or default value, appropriately.
   * Furthermore, if an exception is used, it means that not every input has a return value.
   */
  def head(xs: List[Int]): Int =
    if (xs.nonEmpty) xs.head
    else throw new Exception("No head element")

  /**
   * What if we can improve on the previous function using types?
   * Scala gives us the Option data type which allows us to work with optional data.
   * Option can either be Some(value) or None
   */
  def helmet(xs: List[Int]): Option[Int] =
    if (xs.nonEmpty) Some(xs.head)
    else None

  /**
   * For interests sake, we could use pattern matching on the list to achieve the same result.
   */
  def helmet2(xs: List[Int]): Option[Int] =
    xs match {
      case ::(head, _) => Some(head)
      case Nil => None
    }

  /**
   * Another use case of Option is a safe method for converting a Char to an Int
   */
  def toInt(x: Char): Option[Int] =
    if (x.isDigit) Some(x.toString.toInt)
    else None

  /**
   * How do we go about using the value, if there is one, inside the Option?
   * We can use map, which takes in a function of type A => B, and applies it to the value inside.
   */
  println("Mapping over an Option")
  println(toInt('5'))
  println(toInt('5').map(x => x * 2))
  println(toInt('A').map(x => x * 2))
  println()


  /**
   * What if we want to use multiple Options?
   * We can use map on both Options, but this will give us a result type of Option[Option[A]].
   * However this is not that useful.
   */
  val multipleOptions =
    toInt('5').map(x =>
      helmet(List(1, 2, 3)).map(y =>
        x + y
      )
    )
  println("Mapping over an multiple options")
  println(multipleOptions)
  println()


  /**
   * To overcome this, we use flatMap.
   */
  val flatMapExample = toInt('5').flatMap(x =>
    helmet(List(1, 2, 3)).map(y =>
      x + y
    )
  )
  println("Using flatMap")
  println(flatMapExample)
  println()


  /**
   * However, if the number of options increases, writing nested flatMaps becomes ugly quickly.
   */
  val nestedFlatMaps = toInt('5').flatMap(x =>
    helmet(List(1, 2, 3)).flatMap(y =>
      toInt('4').map(z =>
        x + y + z
      )
    )
  )
  println("Nested flatMaps")
  println(nestedFlatMaps)
  println()

  /**
   * Scala allows us to write nested flatMaps as a for comprehension.
   */
  val comp = for {
    x <- toInt('5')
    y <- helmet(List(1, 2, 3))
    z <- toInt('4')
  } yield x + y + z
  println("For comprehension")
  println(comp)
  println()

  /**
   * If at any point a None is encountered, then the overall type will be None.
   */
  val failedComp = for {
    x <- toInt('5')
    y <- helmet(List(1, 2, 3))
    z <- toInt('A')
  } yield x + y + z
  println("Failed for comprehension")
  println(failedComp)
  println()

  /**
   * Lastly there are a few ways to extract the value from with an option.
   */
  println("Extracting values outside of Option")
  println(comp.getOrElse(0.0)) // Default value
  println(failedComp.map(_.toString).getOrElse("Something went wrong"))
  println(comp.get) // Unsafe
  comp match {
    case Some(value) => println(value)
    case None => println("Something went wrong")
  }
}
