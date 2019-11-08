PHONY: build-verilator
build-verilator:
	-verilator -Wno-STMTDLY --cc ProgramCounter.v --exe eita.cpp
	make -j -C obj_dir -f VProgramCounter.mk VProgramCounter

start-verilator:
	obj_dir/VProgramCounter
