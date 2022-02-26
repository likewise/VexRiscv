package vexriscv

import java.awt
import java.awt.event.{ActionEvent, ActionListener, MouseEvent, MouseListener}

import spinal.sim._
import spinal.core._
import spinal.core.sim._
import vexriscv.demo.{Finka, FinkaConfig}
import javax.swing._

import spinal.lib.com.jtag.sim.JtagTcp
import spinal.lib.com.uart.sim.{UartDecoder, UartEncoder}
import vexriscv.test.{JLedArray, JSwitchArray}

import scala.collection.mutable

object FinkaSim {
  def main(args: Array[String]): Unit = {
    def config = FinkaConfig.default.copy(
      /*onChipRamHexFile = "src/main/c/finka/hello_world/build/hello_world.hex",*/
      onChipRamSize = 64 kB
    )
    val simSlowDown = false
    SimConfig.allOptimisation.compile(new Finka(config)).doSimUntilVoid{dut =>

      /* mainClk is the axi clock */
      val mainClkPeriod = (1e12/dut.config.axiFrequency.toDouble).toLong
      val jtagClkPeriod = mainClkPeriod * 4/* this must be 4 (maybe more, not less) */
      val uartBaudRate = 115200
      val uartBaudPeriod = (1e12/uartBaudRate).toLong

      val clockDomain = ClockDomain(dut.io.axiClk, dut.io.asyncReset)
      clockDomain.forkStimulus(mainClkPeriod)
      clockDomain.forkSimSpeedPrinter(60)

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

      //val timerThread = fork{
      //  var counter = 0;
      //  while (true) {
      //    val counter_overflow = (counter == dut.config.axiFrequency.toInt)
      //    dut.io.timerExternal.tick #= counter_overflow
      //    if (counter_overflow) {
      //      counter == 0
      //    }
      //    counter += 1
      //  }
      //}

      val guiThread = fork{
        val guiToSim = mutable.Queue[Any]()

        var ledsValue = 0l
        var switchValue : () => BigInt = null
        val ledsFrame = new JFrame{
          setLayout(new BoxLayout(getContentPane, BoxLayout.Y_AXIS))

          add(new JLedArray(8){
            override def getValue = ledsValue
          })
          add{
            val switches = new JSwitchArray(8)
            switchValue = switches.getValue
            switches
          }

          add(new JButton("Reset"){
            addActionListener(new ActionListener {
              override def actionPerformed(actionEvent: ActionEvent): Unit = {
                println("ASYNC RESET")
                guiToSim.enqueue("asyncReset")
              }
            })
            setAlignmentX(awt.Component.CENTER_ALIGNMENT)
          })
          setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
          pack()
          setVisible(true)
        }

        //Fast refresh
//        clockDomain.onSampling{
//          dut.io.gpioA.read #= (dut.io.gpioA.write.toLong & dut.io.gpioA.writeEnable.toLong) | (switchValue() << 8)
//        }

        //Slow refresh
        while(true){
          sleep(mainClkPeriod*500000)

          val dummy = if(guiToSim.nonEmpty){
            val request = guiToSim.dequeue()
            if(request == "asyncReset"){
              dut.io.asyncReset #= true
              sleep(mainClkPeriod*32)
              dut.io.asyncReset #= false
            }
          }

          dut.io.gpioA.read #= (dut.io.gpioA.write.toLong & dut.io.gpioA.writeEnable.toLong) | (switchValue() << 8)
          ledsValue = dut.io.gpioA.write.toLong
          ledsFrame.repaint()
          if(simSlowDown) Thread.sleep(400)
        }
      }
    }
  }
}
