#include "VALU32Bit.h"
#include "verilated.h"

enum Command {
    ALUControl,
    A,
    B,
    Eval
};

enum Output {
    OutputALUResult,
    OutputZero
};

extern "C"
void process_command(VALU32Bit* top,
                     Command command,
                     long command_value) {
    switch(command) {
    case ALUControl: {
        top->ALUControl = command_value;
        break;
    }
    case A: {
        top->A = command_value;
        break;
    }
    case B: {
        top->B = command_value;
        break;
    }
    case Eval: {
        top->eval();
        break;
    }
    }
}

extern "C"
VALU32Bit* create_module() {
    char *args[] = {
        NULL
    };

    Verilated::commandArgs(0, args);
    return new VALU32Bit();
}

extern "C"
int* read_module(VALU32Bit* top) {
    static int m[2];
    m[OutputALUResult] = top->ALUResult;
    m[OutputZero] = top->Zero;
    return m;
}

int main(int argc, char** argv, char** env) {
    exit(1);
}
