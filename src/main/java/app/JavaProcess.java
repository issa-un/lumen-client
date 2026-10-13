package app;

record JavaProcess(String pid, String executable) {
    @Override
    public String toString() {
        return executable + " (PID " + pid + ")";
    }
}