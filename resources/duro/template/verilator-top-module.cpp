#include "verilated.h"
#include "generated_template.h"
#include <iostream>
#include <cstdio>

long long input[INPUT_SIZE];
long long output[OUTPUT_SIZE];
int eval_flags[2];

extern "C"
long long* get_input_pointer() {
    return input;
}

extern "C"
long long* get_output_pointer() {
    return output;
}

extern "C"
int* get_eval_flags_pointer() {
    return eval_flags;
}

extern "C"
void set_local_signal(TOP_CLASS* top, int sig, int arg) {
    GENERATED_LOCAL_SIGNAL_INPUTS
    GENERATED_INDEPENDENT_SIGNAL_INPUTS
}

extern "C"
long long get_local_signal(TOP_CLASS* top, int sig) {
    GENERATED_LOCAL_SIGNAL_OUTPUTS
    GENERATED_INDEPENDENT_SIGNAL_OUTPUTS
    return 10101010; //default
}

extern "C"
void set_array_signal(TOP_CLASS* top, int sig, int idx, int arg) {
    GENERATED_ARRAY_SIGNAL_INPUTS
}

extern "C"
int get_array_signal(TOP_CLASS* top, int sig, int idx) {
    GENERATED_ARRAY_SIGNAL_OUTPUTS
    return 10101010; //default
}

extern "C"
int eval(TOP_CLASS* top) {
    while (eval_flags[1] != 0) {
        if (eval_flags[0] != 0) {
            GENERATED_INPUTS
            top->eval();
            GENERATED_OUTPUTS
            eval_flags[0] = 0;
        }
    }
    return 9999;
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
