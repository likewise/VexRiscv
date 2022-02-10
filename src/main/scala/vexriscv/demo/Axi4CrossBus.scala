import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi._

class Axi4CrossBus extends Component{

//Interface signal definition.
val ma  = master(Axi4(Axi4Config(32,64,2)))
val mb  = master(Axi4(Axi4Config(32,64,2)))
val mc  = master(Axi4(Axi4Config(32,64,2)))
val sa  = slave (Axi4(Axi4Config(32,64,2)))
val sb  = slave (Axi4(Axi4Config(32,64,2)))


//local signal definition.
val m0  = Axi4(Axi4Config(32,64,2))
val m1  = Axi4(Axi4Config(32,64,2))
val s1  = Axi4(Axi4Config(32,64,2))
val s2  = Axi4(Axi4Config(32,64,2))
val s3  = Axi4(Axi4Config(32,64,2))

//Interface axi signal connection.(sa->ma,mb)(sb->mc)
s1 <> ma
s2 <> mb
s3 <> mc
m0 <> sa
m1 <> sb

//axi crossbar Factory.
val axiCrossbar = Axi4CrossbarFactory()

//add slaves.
axiCrossbar.addSlaves(
    s1 -> (0x00000000L,   4 KiB),
    s2 -> (0x40000000L,  32 MiB),
    s3 -> (0x80000000L,  1  MiB)
)

//add connection.
axiCrossbar.addConnections(
    m0  -> List(s1, s2),
    //m1  -> List(s3)   //success.
    m1  -> List(s2, s3) //failed.
)

//generate axi crossbar.
axiCrossbar.build()
}

//Verilog generator.
object Axi4CrossBusGen {
    def main(args: Array[String]) {
        SpinalVerilog(new Axi4CrossBus)
    }
}
