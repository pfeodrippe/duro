#include "VALU32Bit.h"
#include "verilated.h"

enum Command {
    ALUControl,
    A,
    B
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
    }
}

static int output[2];

extern "C"
void eval(VALU32Bit* top) {
    top->eval();
    output[OutputALUResult] = top->ALUResult;
    output[OutputZero] = top->Zero;
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
int* get_output_pointer() {
    return output;
}

int main(int argc, char** argv, char** env) {
    exit(1);
}
