package top.dteam.dgate.utils

class TestUtils {

    static void waitResult(def result, long timeout) {
        int i = 0
        while (!result && i < timeout) {
            sleep(1)
            i++
        }
    }

}
