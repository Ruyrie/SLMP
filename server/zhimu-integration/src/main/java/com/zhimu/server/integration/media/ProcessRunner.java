package com.zhimu.server.integration.media;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 外部进程调用：设置超时，超时销毁进程；收集 stderr 最后若干行作为失败原因。
 */
final class ProcessRunner {

    static final int STDERR_TAIL_LINES = 15;

    private ProcessRunner() {
    }

    record Result(int exitCode, String stdout, String stderrTail) {
    }

    /**
     * @param stdout stdout 的逐行处理；为 null 时收集到结果的 stdout 中
     */
    static Result run(List<String> command, Duration timeout, Consumer<String> stdout) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
            process.getOutputStream().close();
        } catch (IOException e) {
            throw new MediaException("音视频处理工具不可用", command.getFirst() + "：" + e.getMessage());
        }
        StringBuilder collected = new StringBuilder();
        Deque<String> tail = new ArrayDeque<>();
        Thread outReader = Thread.ofVirtual().start(() -> readLines(process.getInputStream(), line -> {
            if (stdout != null) {
                stdout.accept(line);
            } else {
                collected.append(line).append('\n');
            }
        }));
        Thread errReader = Thread.ofVirtual().start(() -> readLines(process.getErrorStream(), line -> {
            synchronized (tail) {
                tail.addLast(line);
                if (tail.size() > STDERR_TAIL_LINES) {
                    tail.removeFirst();
                }
            }
        }));
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new MediaException("音视频处理被中断", null);
        }
        if (!finished) {
            process.destroyForcibly();
            join(outReader);
            join(errReader);
            throw new MediaException("音视频处理超时", "超过 " + timeout.toSeconds() + " 秒未完成，已终止进程；"
                    + tailText(tail));
        }
        join(outReader);
        join(errReader);
        return new Result(process.exitValue(), collected.toString(), tailText(tail));
    }

    private static void readLines(InputStream in, Consumer<String> consumer) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                consumer.accept(line);
            }
        } catch (IOException ignored) {
            // 进程被销毁时流关闭
        }
    }

    private static void join(Thread t) {
        try {
            t.join(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String tailText(Deque<String> tail) {
        synchronized (tail) {
            return String.join("\n", tail);
        }
    }
}
