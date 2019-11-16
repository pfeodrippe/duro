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

static int input[3];
static int output[2];
static int eval_flags[2];

extern "C"
int* get_input_pointer() {
    return input;
}

extern "C"
int* get_output_pointer() {
    return output;
}

extern "C"
int* get_eval_flags_pointer() {
    return eval_flags;
}

extern "C"
void eval(VALU32Bit* top) {
    while (eval_flags[1] != 0) {
        if (eval_flags[0] != 0) {
            top->ALUControl = input[ALUControl];
            top->A = input[A];
            top->B = input[B];
            top->eval();
            output[OutputALUResult] = top->ALUResult;
            output[OutputZero] = top->Zero;
            eval_flags[0] = 0;
        }
    }
}

extern "C"
VALU32Bit* create_module() {
    char *args[] = {
        NULL
    };

    eval_flags[0] = 0;
    eval_flags[1] = 1;
    Verilated::commandArgs(0, args);
    return new VALU32Bit();
}

int main(int argc, char** argv, char** env) {
    exit(1);
}
