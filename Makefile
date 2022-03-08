.ONESHELL:

.PHONY: rtl debug_in_sim mrproper

# in background run batched program load and run via GDB
# while the simulator is running
# afterwards manually inspect waveform using "make waveform"
debug_in_sim:
	rm -rf sbt.log
	make sim_batch_debug &
	PIDGDB=$$!
	echo PIDGDB=$$PIDGDB
	make sim
	echo "\nTEST RESULT"=$$?
	#kill -9 $$PIDGDB

# load and run via GDB in batch mode, after detecting the JTAG TCP
sim_batch_debug:
	set -e
	# @TODO maybe do not rely on log, but on netstat -tln | grep port?
	tail -F sbt.log | sed '/WAITING FOR TCP JTAG CONNECTION/ q' > /dev/null
	make -C src/main/c/facet/hello_world   batch_debug    DEBUG=yes

# build program for SoC, and RTL of SoC
rtl:
	set -e
	make -j8 -C src/main/c/facet/hello_world clean
	make -j8 -C src/main/c/facet/hello_world clean all DEBUG=yes
	sbt "runMain vexriscv.demo.FacetWithMemoryInit"

# run in terminal #1
sim: use_upstream_spinal #use_dev_spinal
	set -e
	rm -rf sbt.log
	make -j8 -C src/main/c/facet/hello_world clean
	make -j8 -C src/main/c/facet/hello_world all DEBUG=yes
	(sbt "runMain vexriscv.demo.FacetSim" | tee sbt.log)
	# @TODO this one could drive some automated tests
	#(sbt "test:runMain vexriscv.FacetSim" | tee sbt.log)

# run in terminal #2
# load and run via GDB in batch mode
debug:
	set -e
	tail -F sbt.log | sed '/WAITING FOR TCP JTAG CONNECTION/ q' > /dev/null
	make -C src/main/c/facet/hello_world   debug    DEBUG=yes

# run in terminal #3
waveform:
	gtkwave -f simWorkspace/Facet/test.vcd -a 00C000000.gtkw &

# this supports building against an upstream ../SpinalHDL checkout
# extract SpinalHDL version from ../SpinalHDL/project/Version.scala
# then fill this in into build.sbt
use_dev_spinal:
	git show origin/dev:build.sbt > build.sbt
	VERSION=1.6.5
	VERSION=`grep -e 'major.*=' ../SpinalHDL/project/Version.scala | sed 's@.*major.*=.*"\(.*\)"$$@\1@'`
	@echo "Found ../SpinalHDL/ version $$VERSION, using this for our build.sbt"
	sed -i "s@val.*spinalVersion.*=.*@val spinalVersion = \"$${VERSION}\"@;" build.sbt

use_upstream_spinal:
	git checkout build.sbt

rtl:
	sbt "runMain vexriscv.demo.FacetWithMemoryInit"

mrproper:
	sbt clean
	rm -fr ~/.ivy2 ~/.sbt
