if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "C:/Users/kkmna/.gradle/caches/8.11.1/transforms/4ee777f4276a3d23418497fb7132cee5/transformed/oboe-1.9.3/prefab/modules/oboe/libs/android.arm64-v8a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "C:/Users/kkmna/.gradle/caches/8.11.1/transforms/4ee777f4276a3d23418497fb7132cee5/transformed/oboe-1.9.3/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

