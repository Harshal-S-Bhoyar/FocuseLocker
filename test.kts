import java.text.Normalizer
fun main() {
    val input = "ca$ino"
    val nfd = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
    println(nfd)
}
