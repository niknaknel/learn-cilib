package example

object Utilities {

  def seconds(x: Long): Double =
    BigDecimal(x / 1000.0).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

}
