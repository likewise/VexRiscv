.ONESHELL:

.PHONY: all debug_in_sim mrproper

# in background run batched program load and run via GDB
# while the simulator is running
# afterwards manually inspect waveform using "make waveform"
debug_in_sim:
	rm -rf sbt.log
	make sim_batch_debug &
	PIDGDB=$$!
	make sim
	echo $$PIDGDB
	kill -9 $$PIDGDB

# load and run via GDB in batch mode, after detecting JTAG TCP
sim_batch_debug:
	set -e
	# @TODO maybe do not rely on log, but on netstat -tln | grep port?
	tail -F sbt.log | sed '/WAITING FOR TCP JTAG CONNECTION/ q' > /dev/null
	make -C src/main/c/finka/hello_world   batch_debug    DEBUG=yes

# build program for SoC, and RTL of SoC
all:
	set -e
	make -j8 -C src/main/c/finka/hello_world clean
	make -j8 -C src/main/c/finka/hello_world clean all DEBUG=yes
	sbt "runMain vexriscv.demo.FinkaWithMemoryInit"

# run in terminal #1
sim: use_dev_spinal
	set -e
	rm -rf sbt.log
	make -j8 -C src/main/c/finka/hello_world clean
	make -j8 -C src/main/c/finka/hello_world all DEBUG=yes
	(sbt "runMain vexriscv.demo.FinkaSim" | tee sbt.log)
	# @TODO this one could drive some automated tests
	#(sbt "test:runMain vexriscv.FinkaSim" | tee sbt.log)

# run in terminal #2
# load and run via GDB in batch mode
debug:
	set -e
	tail -F sbt.log | sed '/WAITING FOR TCP JTAG CONNECTION/ q' > /dev/null
	make -C src/main/c/finka/hello_world   debug    DEBUG=yes

# run in terminal #3
waveform:
	gtkwave -f simWorkspace/Finka/test.vcd -a 00C000000.gtkw &

use_dev_spinal:
	git show origin/dev:build.sbt > build.sbt
	sed -i 's@val.*spinalVersion.*=.*@val spinalVersion = "1.6.5"@;' build.sbt
	# @TODO: get this from ../SpinalHDL/project/Version.scala:7:  private val major = "1.6.5"

use_upstream_spinal:
	git checkout build.sbt

rtl:
	sbt "runMain vexriscv.demo.FinkaWithMemoryInit"

mrproper:
	sbt clean
	rm -fr ~/.ivy2 ~/.sbt
