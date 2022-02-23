.ONESHELL:

.PHONY: all

all:
	set -e
	make -C src/main/c/finka/hello_world clean all DEBUG=yes
	sbt "runMain vexriscv.demo.FinkaWithMemoryInit"

sim:
	set -e
	make -C src/main/c/finka/hello_world clean all DEBUG=yes
	(sbt "runMain vexriscv.demo.FinkaSim" | tee sbt.log)
#	sleep 20
#	grep 'WAITING FOR TCP JTAG CONNECTION' sbt.log && make -C src/main/c/finka/hello_world debug DEBUG=yes

debug:
	grep 'WAITING FOR TCP JTAG CONNECTION' sbt.log && make -C src/main/c/finka/hello_world debug DEBUG=yes

rtl:
	sbt "runMain vexriscv.demo.FinkaWithMemoryInit"
	