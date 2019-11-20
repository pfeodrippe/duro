#include "verilated.h"
#include "generated_template.h"

static int input[INPUT_SIZE];
static int output[OUTPUT_SIZE];
static int local_signal[LOCAL_SIGNAL_SIZE];
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
int* get_local_signal_pointer() {
    return local_signal;
}

extern "C"
int* get_eval_flags_pointer() {
    return eval_flags;
}

extern "C"
void set_submodule_local_signal(TOP_CLASS* top, int sig, int arg) {
    GENERATED_SUBMODULE_SIGNAL_INPUTS
}

extern "C"
int get_submodule_local_signal(TOP_CLASS* top, int sig) {
    GENERATED_SUBMODULE_SIGNAL_OUTPUTS
    return -99999;
}

extern "C"
void eval(TOP_CLASS* top) {
    while (eval_flags[1] != 0) {
        if (eval_flags[0] != 0) {
            GENERATED_INPUTS
            top->eval();
            GENERATED_OUTPUTS
            eval_flags[0] = 0;
        }
    }
}

extern "C"
void eval_with_debug(TOP_CLASS* top) {
    while (eval_flags[1] != 0) {
        if (eval_flags[0] != 0) {
            GENERATED_INPUTS
            GENERATED_LOCAL_SIGNAL_INPUTS
            top->eval();
            GENERATED_OUTPUTS
            GENERATED_LOCAL_SIGNAL_OUTPUTS
            eval_flags[0] = 0;
        }
    }
}

extern "C"
TOP_CLASS* create_module() {
    char *args[] = {
        NULL
    };

    eval_flags[0] = 0;
    eval_flags[1] = 1;
    Verilated::commandArgs(0, args);
    return new TOP_CLASS();
}

int main(int argc, char** argv, char** env) {
    exit(1);
}
