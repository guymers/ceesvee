package ceesvee

def VectorAPIAvailable: Boolean = ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent
