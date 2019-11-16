PHONY: build-verilator
build-verilator:
	rm -rf obj_dir
	-verilator -Wno-STMTDLY --cc ProgramCounter.v --exe eita.cpp
	make -j -C obj_dir -f VProgramCounter.mk VProgramCounter

PHONY: build-alu
build-alu:
	rm -rf obj_dir
	-verilator -Wno-STMTDLY --cc ALU32Bit.v --exe template.cpp
	-verilator -Wno-STMTDLY --xml-only ALU32Bit.v
	make -j -C obj_dir -f VALU32Bit.mk VALU32Bit
	cd obj_dir && gcc -shared -o libfob40.dylib *.o -lstdc++

start-verilator:
	obj_dir/VProgramCounter

start-alu:
	obj_dir/VALU32Bit
