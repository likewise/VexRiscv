// Based on Briey, but with VGA and SDRAM controller removed

// Goal 1 is to expose a full (non-shared) AXI4 master on the top-level.
// see "extAxiSharedBus" for the bus between crossbar and this master
// see "extAxi4Master" for the master interface for toplevel I/O
// This works, tested in hardware.

// Goal 2 is to expose a full (non-shared) AXI4 slave on the top-level.
// see "pcieAxiSharedBus" for the bus between crossbar and this slave
// pcieAxiSharedBus is bridged from pcieAxi4Bus
// see "pcieAxi4Slave" for the slave interface for toplevel I/O
// This compiles.

package vexriscv.demo

import vexriscv.plugin._
import vexriscv._
import vexriscv.ip.{DataCacheConfig, InstructionCacheConfig}
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._
import spinal.lib.com.jtag.Jtag
import spinal.lib.com.jtag.sim.JtagTcp
import spinal.lib.com.uart.sim.{UartDecoder, UartEncoder}
import spinal.lib.com.uart.{Apb3UartCtrl, Uart, UartCtrlGenerics, UartCtrlMemoryMappedConfig}
import spinal.lib.io.TriStateArray
import spinal.lib.misc.HexTools
import spinal.lib.soc.pinsec.{PinsecTimerCtrl, PinsecTimerCtrlExternal}
import spinal.lib.system.debugger.{JtagAxi4SharedDebugger, JtagBridge, SystemDebugger, SystemDebuggerConfig}

import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.regif.AccessType._
import spinal.lib.bus.regif._
import spinal.lib.bus.regif.Document.CHeaderGenerator
import spinal.lib.bus.regif.Document.HtmlGenerator
import spinal.lib.bus.regif.Document.JsonGenerator

import scala.collection.mutable.ArrayBuffer
import scala.collection.Seq

case class FinkaConfig(axiFrequency : HertzNumber,
                       onChipRamSize : BigInt,
                       /*onChipRamHexFile : String,*/
                       cpuPlugins : ArrayBuffer[Plugin[VexRiscv]],
                       uartCtrlConfig : UartCtrlMemoryMappedConfig,
                       pcieAxi4Config : Axi4Config)

object FinkaConfig{

  def default = {
    val config = FinkaConfig(
      axiFrequency = 250 MHz,
      onChipRamSize  = 64 kB,
      uartCtrlConfig = UartCtrlMemoryMappedConfig(
        uartCtrlConfig = UartCtrlGenerics(
          dataWidthMax      = 8,
          clockDividerWidth = 20,
          preSamplingSize   = 1,
          samplingSize      = 5,
          postSamplingSize  = 2
        ),
        txFifoDepth = 16,
        rxFifoDepth = 16
      ),
      /* prot signals but no last signal - however SpinalHDL/Axi4 assumes Last for Axi4* classes */
      pcieAxi4Config = Axi4Config(addressWidth = 32, dataWidth = 32, idWidth = 0, useId = false, useRegion = false, 
        useBurst = false, useLock = false, useCache = false, useSize = false, useQos = false,
        useLen = false, useLast = true/*fails otherwise*/, useResp = true, useProt = true, useStrb = true),
      cpuPlugins = ArrayBuffer(
        new PcManagerSimplePlugin(0x00800000L, false),
        //          new IBusSimplePlugin(
        //            interfaceKeepData = false,
        //            catchAccessFault = true
        //          ),
        new IBusCachedPlugin(
          resetVector = 0x00800000L,
          prediction = STATIC,
          config = InstructionCacheConfig(
            cacheSize = 4096,
            bytePerLine =32,
            wayCount = 1,
            addressWidth = 32,
            cpuDataWidth = 32,
            memDataWidth = 32,
            catchIllegalAccess = true,
            catchAccessFault = true,
            asyncTagMemory = false,
            twoCycleRam = true,
            twoCycleCache = true
          )
          //            askMemoryTranslation = true,
          //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
          //              portTlbSize = 4
          //            )
        ),
        //                    new DBusSimplePlugin(
        //                      catchAddressMisaligned = true,
        //                      catchAccessFault = true
        //                    ),
        new DBusCachedPlugin(
          config = new DataCacheConfig(
            cacheSize         = 4096,
            bytePerLine       = 32,
            wayCount          = 1,
            addressWidth      = 32,
            cpuDataWidth      = 32,
            memDataWidth      = 32,
            catchAccessError  = true,
            catchIllegal      = true,
            catchUnaligned    = true
          ),
          memoryTranslatorPortConfig = null
          //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
          //              portTlbSize = 6
          //            )
        ),
        new StaticMemoryTranslatorPlugin(
          // 0x00C00000-0x00FFFFFF is uncached
          ioRange      = _(23 downto 22) === 0x3
        ),
        new DecoderSimplePlugin(
          catchIllegalInstruction = true
        ),
        new RegFilePlugin(
          regFileReadyKind = plugin.SYNC,
          zeroBoot = false
        ),
        new IntAluPlugin,
        new SrcPlugin(
          separatedAddSub = false,
          executeInsertion = true
        ),
        new FullBarrelShifterPlugin,
        new MulPlugin,
        new DivPlugin,
        new HazardSimplePlugin(
          bypassExecute           = true,
          bypassMemory            = true,
          bypassWriteBack         = true,
          bypassWriteBackBuffer   = true,
          pessimisticUseSrc       = false,
          pessimisticWriteRegFile = false,
          pessimisticAddressMatch = false
        ),
        new BranchPlugin(
          earlyBranch = false,
          catchAddressMisaligned = true
        ),
        new CsrPlugin(
          config = CsrPluginConfig(
            catchIllegalAccess = false,
            mvendorid      = null,
            marchid        = null,
            mimpid         = null,
            mhartid        = null,
            misaExtensionsInit = 66,
            misaAccess     = CsrAccess.NONE,
            mtvecAccess    = CsrAccess.NONE,
            mtvecInit      = 0x00800020l,
            mepcAccess     = CsrAccess.READ_WRITE,
            mscratchGen    = false,
            mcauseAccess   = CsrAccess.READ_ONLY,
            mbadaddrAccess = CsrAccess.READ_ONLY,
            mcycleAccess   = CsrAccess.NONE,
            minstretAccess = CsrAccess.NONE,
            ecallGen       = false,
            wfiGenAsWait   = false,
            ucycleAccess   = CsrAccess.NONE,
            uinstretAccess = CsrAccess.NONE
          )
        ),
        new YamlPlugin("cpu0.yaml")
      )
    )
    config
  }
}

class Finka(val config: FinkaConfig) extends Component{

  //Legacy constructor
  def this(axiFrequency: HertzNumber) {
    this(FinkaConfig.default.copy(axiFrequency = axiFrequency))
  }

  import config._
  val debug = true
  val interruptCount = 4

  val io = new Bundle{
    // Clocks / reset
    val asyncReset = in Bool()

    val axiClk     = in Bool()

    val packetClk   = in Bool()
    //val packetRst   = in Bool()

    // Main components IO
    val jtag       = slave(Jtag())

    // AXI4 master towards an external AXI4 peripheral
    //val extAxi4Master = master(Axi4(Axi4Config(32, 32, 2, useQos = false, useRegion = false)))

    // AXI4 slave from (external) PCIe bridge
    val pcieAxi4Slave = slave(Axi4(pcieAxi4Config))

    // Peripherals IO
    val gpioA         = master(TriStateArray(32 bits))
    val uart          = master(Uart())
    val timerExternal = in(PinsecTimerCtrlExternal())
    val coreInterrupt = in Bool()

    val update = out UInt(64 bits)
    val commit = out Bool()
  }

  val resetCtrlClockDomain = ClockDomain(
    clock = io.axiClk,
    config = ClockDomainConfig(
      resetKind = BOOT
    )
  )

  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
    val systemResetUnbuffered  = False
    //    val coreResetUnbuffered = False

    //Implement an counter to keep the reset axiResetOrder high 64 cycles
    // Also this counter will automaticly do a reset when the system boot.
    val systemResetCounter = Reg(UInt(6 bits)) init(0)
    when(systemResetCounter =/= U(systemResetCounter.range -> true)){
      systemResetCounter := systemResetCounter + 1
      systemResetUnbuffered := True
    }
    when(BufferCC(io.asyncReset)){
      systemResetCounter := 0
    }

    //Create all reset used later in the design
    val systemReset  = RegNext(systemResetUnbuffered)
    val axiReset     = RegNext(systemResetUnbuffered)
    val packetReset  = RegNext(systemResetUnbuffered)
  }

  val axiClockDomain = ClockDomain(
    clock = io.axiClk,
    reset = resetCtrl.axiReset,
    frequency = FixedFrequency(axiFrequency) //The frequency information is used by the SDRAM controller
  )

  val debugClockDomain = ClockDomain(
    clock = io.axiClk,
    reset = resetCtrl.systemReset,
    frequency = FixedFrequency(axiFrequency)
  )

  val packetClockDomain = ClockDomain(
    clock = io.packetClk,
    reset = resetCtrl.packetReset /*BufferCC(resetCtrl.systemResetUnbuffered)*/
  )

  val axi = new ClockingArea(axiClockDomain) {

    val ram = Axi4SharedOnChipRam(
      dataWidth = 32,
      byteCount = onChipRamSize,
      idWidth = 4
    )

    val extAxiSharedBus = Axi4Shared(Axi4Config(32, 32, 2, useQos = false, useRegion = false))

    val pcieAxi4Bus = Axi4(pcieAxi4Config)
    val pcieAxiSharedBus = pcieAxi4Bus.toShared()

    //, useId = false, useRegion = false, 
    // useBurst = false, useLock = false, useCache = false, useSize = false, useQos = false,
    // useLen = false, useLast = false, useResp = false, useProt = true, useStrb = false))

    val apbBridge = Axi4SharedToApb3Bridge(
      addressWidth = 20,
      dataWidth    = 32,
      idWidth      = 4
    )

    val gpioACtrl = Apb3Gpio(
      gpioWidth = 32,
      withReadSync = true
    )
    val timerCtrl = PinsecTimerCtrl()

    val uartCtrl = Apb3UartCtrl(uartCtrlConfig)
    uartCtrl.io.apb.addAttribute(Verilator.public)

    val core = new Area{
      val config = VexRiscvConfig(
        plugins = cpuPlugins += new DebugPlugin(debugClockDomain, 3/*breakpoints*/)
      )

      val cpu = new VexRiscv(config)
      var iBus : Axi4ReadOnly = null
      var dBus : Axi4Shared = null
      for(plugin <- config.plugins) plugin match{
        case plugin : IBusSimplePlugin => iBus = plugin.iBus.toAxi4ReadOnly()
        case plugin : IBusCachedPlugin => iBus = plugin.iBus.toAxi4ReadOnly()
        case plugin : DBusSimplePlugin => dBus = plugin.dBus.toAxi4Shared()
        case plugin : DBusCachedPlugin => dBus = plugin.dBus.toAxi4Shared(true/*stageCmd required (?)*/)
        case plugin : CsrPlugin        => {
          plugin.externalInterrupt := BufferCC(io.coreInterrupt)
          plugin.timerInterrupt := timerCtrl.io.interrupt
        }
        case plugin : DebugPlugin      => debugClockDomain{
          resetCtrl.axiReset setWhen(RegNext(plugin.io.resetOut))
          io.jtag <> plugin.io.bus.fromJtag()
        }
        case _ =>
      }
    }

    val axiCrossbar = Axi4CrossbarFactory()

    axiCrossbar.addSlaves(
      ram.io.axi       -> (0x00800000L, onChipRamSize),
      extAxiSharedBus  -> (0x00C00000L, 3 MB),
      apbBridge.io.axi -> (0x00F00000L, 1 MB)
    )

    // sparse AXI4Shared crossbar
    axiCrossbar.addConnections(
      // CPU instruction bus (read-only master) can only access RAM slave
      core.iBus        -> List(ram.io.axi),
      // CPU data bus (read-only master) can access all slaves
      core.dBus        -> List(ram.io.axi, apbBridge.io.axi, extAxiSharedBus),
      pcieAxiSharedBus -> List(ram.io.axi, apbBridge.io.axi, extAxiSharedBus)
    )

    axiCrossbar.addPipelining(apbBridge.io.axi)((crossbar,bridge) => {
      crossbar.sharedCmd.halfPipe() >> bridge.sharedCmd
      crossbar.writeData.halfPipe() >> bridge.writeData
      crossbar.writeRsp             << bridge.writeRsp
      crossbar.readRsp              << bridge.readRsp
    })

    axiCrossbar.addPipelining(extAxiSharedBus)((crossbar,ctrl) => {
      crossbar.sharedCmd.halfPipe() >> ctrl.sharedCmd
      crossbar.writeData            >/-> ctrl.writeData
      crossbar.writeRsp              <<  ctrl.writeRsp
      crossbar.readRsp               <<  ctrl.readRsp
    })

    axiCrossbar.addPipelining(ram.io.axi)((crossbar,ctrl) => {
      crossbar.sharedCmd.halfPipe()  >>  ctrl.sharedCmd
      crossbar.writeData            >/-> ctrl.writeData
      crossbar.writeRsp              <<  ctrl.writeRsp
      crossbar.readRsp               <<  ctrl.readRsp
    })

    axiCrossbar.addPipelining(core.dBus)((cpu,crossbar) => {
      cpu.sharedCmd             >>  crossbar.sharedCmd
      cpu.writeData             >>  crossbar.writeData
      cpu.writeRsp              <<  crossbar.writeRsp
      cpu.readRsp               <-< crossbar.readRsp //Data cache directly use read responses without buffering, so pipeline it for FMax
    })

    axiCrossbar.addPipelining(pcieAxiSharedBus)((pcie,crossbar) => {
      pcie.sharedCmd             >>  crossbar.sharedCmd
      pcie.writeData             >>  crossbar.writeData
      pcie.writeRsp              <<  crossbar.writeRsp
      pcie.readRsp               <<  crossbar.readRsp
    })

    axiCrossbar.build()

    val apbDecoder = Apb3Decoder(
      master = apbBridge.io.apb,
      slaves = List(
        gpioACtrl.io.apb -> (0x00000, 4 kB),
        uartCtrl.io.apb  -> (0x10000, 4 kB),
        timerCtrl.io.apb -> (0x20000, 4 kB)
      )
    )
  }

  /* attempt to bring extAxiSharedBus : Axi4SharedBus from axi to packet ClockDomain */

  val packet = new ClockingArea(packetClockDomain) {
    val packetAxi4Bus = Axi4(Axi4Config(32, 32, 2, useQos = false, useRegion = false))
    //val packetAxi4Shared = Axi4Shared(Axi4Config(32, 32, 2, useQos = false, useRegion = false))

      val ctrl = new Axi4SlaveFactory(packetAxi4Bus)
      val reg1 = ctrl.createWriteOnly(UInt(32 bits), address = 0x00C00000L, bitOffset = 0)
      val reg2 = ctrl.createWriteOnly(UInt(32 bits), address = 0x00C00004L, bitOffset = 0)
      val update = UInt(64 bits)

      //ctrl.onWrite(0x00C00004L) {
        update := reg1 @@ reg2
      //}
      val commit = ctrl.isWriting(address = 0x00C00000L)
  }

  val axi2packetCDC = Axi4SharedCC(Axi4Config(32, 32, 2, useQos = false, useRegion = false), axiClockDomain, packetClockDomain, 2, 2, 2, 2)
  axi2packetCDC.io.input << axi.extAxiSharedBus
  //  packet.packetAxi4Shared << axi2packetCDC.io.output
  //  packet.packetAxi4Bus << packet.packetAxi4Shared.toAxi4()

  // first connect Axi4CDC into packet clock domain
  packet.packetAxi4Bus << axi2packetCDC.io.output.toAxi4()
  // ..and then to I/O
  //io.extAxi4Master << packet.packetAxi4Bus

  // OR... directly output a Axi4 master in packet clock domain
  //io.extAxi4Master << axi2packetCDC.io.output.toAxi4()

  // in case extAxiSharedBus and io.extAxi4Master remains in axi clock domain
  //io.extAxi4Master  <> axi.extAxiSharedBus.toAxi4()

  io.gpioA          <> axi.gpioACtrl.io.gpio
  io.timerExternal  <> axi.timerCtrl.io.external
  io.uart           <> axi.uartCtrl.io.uart
  io.pcieAxi4Slave  <> axi.pcieAxi4Bus

  io.commit := packet.commit
  io.update := packet.update

  //noIoPrefix()
}

// https://gitter.im/SpinalHDL/SpinalHDL?at=5c2297c28d31aa78b1f8c969
object XilinxPatch {
  def apply[T <: Component](c : T) : T = {
    //Get the io bundle via java reflection
    val m = c.getClass.getMethod("io")
    val io = m.invoke(c).asInstanceOf[Bundle]

    //Patch things
    io.elements.map(_._2).foreach{
      //case axi : AxiLite4 => AxiLite4SpecRenamer(axi)
      case axi : Axi4 => Axi4SpecRenamer(axi)
      case _ =>
    }

    //Builder pattern return the input argument
    c 
  }
}

object Finka{
  def main(args: Array[String]) {
    val config = SpinalConfig()
    val verilog = config.generateVerilog({
      val toplevel = new Finka(FinkaConfig.default)
      XilinxPatch(toplevel)
    })
    //verilog.printPruned()
  }
}

object FinkaWithMemoryInit{
  def main(args: Array[String]) {
    val config = SpinalConfig()
    val verilog = config.generateVerilog({
      val toplevel = new Finka(FinkaConfig.default)
      HexTools.initRam(toplevel.axi.ram.ram, "src/main/c/finka/hello_world/build/hello_world.hex", 0x00800000L)
      //onChipRamHexFile
      XilinxPatch(toplevel)
    })
    //verilog.printPruned()
  }
}

import spinal.core.sim._
object FinkaSim {
  def main(args: Array[String]): Unit = {
    val simSlowDown = false
    //val toplevel = new Finka(FinkaConfig.default);
    //HexTools.initRam(toplevel.axi.ram.ram, "src/main/c/finka/hello_world/build/hello_world.hex", 0x00800000L)

    SimConfig.allOptimisation.withWave.compile(new Finka(FinkaConfig.default)/*toplevel*/).doSim/*UntilVoid*/{dut =>
    // .withWave
      HexTools.initRam(dut.axi.ram.ram, "src/main/c/finka/hello_world/build/hello_world.hex", 0x00800000L)
      val mainClkPeriod = (1e12/dut.config.axiFrequency.toDouble).toLong
      val jtagClkPeriod = mainClkPeriod*4
      val uartBaudRate = 115200
      val uartBaudPeriod = (1e12/uartBaudRate).toLong

      val axiClockDomain = ClockDomain(dut.io.axiClk, dut.io.asyncReset)
      axiClockDomain.forkStimulus(mainClkPeriod)

      val packetClockDomain = ClockDomain(dut.io.packetClk)
      packetClockDomain.forkStimulus((1e12/322e6).toLong)

      val tcpJtag = JtagTcp(
        jtag = dut.io.jtag,
        jtagClkPeriod = jtagClkPeriod
      )

      val uartTx = UartDecoder(
        uartPin = dut.io.uart.txd,
        baudPeriod = uartBaudPeriod
      )

      val uartRx = UartEncoder(
        uartPin = dut.io.uart.rxd,
        baudPeriod = uartBaudPeriod
      )

      dut.io.coreInterrupt #= false

      var commits_seen = 0
      var cycles_post = 10

      while (true) {
        if (dut.io.commit.toBoolean) {
          println("COMMIT #", commits_seen)
          commits_seen += 1
        }
        packetClockDomain.waitRisingEdge()
        if (commits_seen > 4) cycles_post -= 1
        if (cycles_post == 0) simSuccess()
      }
      simSuccess()
    }
  }
}
