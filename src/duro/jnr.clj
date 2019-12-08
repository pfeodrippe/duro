(ns duro.jnr
  (:import
   (jnr.ffi Pointer)))

(definterface NativeLibInterface
  (^jnr.ffi.Pointer create_module [])
  (^jnr.ffi.Pointer get_input_pointer [])
  (^jnr.ffi.Pointer get_output_pointer [])
  (^jnr.ffi.Pointer get_eval_flags_pointer [])
  (^int set_local_signal [^jnr.ffi.Pointer top ^int sig ^int arg])
  (^long get_local_signal [^jnr.ffi.Pointer top ^int sig])
  (^int set_array_signal [^jnr.ffi.Pointer top ^int sig ^int idx ^int arg])
  (^int get_array_signal [^jnr.ffi.Pointer top ^int sig ^int idx])
  (^int eval [^jnr.ffi.Pointer top]))
