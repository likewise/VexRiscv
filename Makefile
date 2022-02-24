.ONESHELL:

.PHONY: all

all:
	set -e
	make -C src/main/c/finka/hello_world clean all DEBUG=yes
	sbt "runMain vexriscv.demo.FinkaWithMemoryInit"

# run in terminal #1
sim: use_dev_spinal
	set -e
	make -C src/main/c/finka/hello_world clean all DEBUG=yes
	(sbt "runMain vexriscv.demo.FinkaSim" | tee sbt.log)
#	sleep 20
#	grep 'WAITING FOR TCP JTAG CONNECTION' sbt.log && make -C src/main/c/finka/hello_world debug DEBUG=yes

# run in terminal #2
debug:
	grep 'WAITING FOR TCP JTAG CONNECTION' sbt.log && make -C src/main/c/finka/hello_world debug DEBUG=yes

# run in terminal #3
waveform:
	gtkwave -f simWorkspace/Finka/test.vcd -a 00C000000.gtkw &

use_dev_spinal:
	git show origin/dev:build.sbt > build.sbt
	sed -i 's@val.*spinalVersion.*=.*@val spinalVersion = "1.6.5"@;' build.sbt
	# @TODO: get this from ../SpinalHDL/project/Version.scala:7:  private val major = "1.6.5"

use_upstream_spinal:
	git checkout build.sbt

debug2:
	grep 'WAITING FOR TCP JTAG CONNECTION' sbt.log && make -C src/main/c/finka/hello_world debug2 DEBUG=yes

rtl:
	sbt "runMain vexriscv.demo.FinkaWithMemoryInit"

mrproper:
	sbt clean
	rm -fr ~/.ivy2 ~/.sbt
