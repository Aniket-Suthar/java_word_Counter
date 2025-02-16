import java.io.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class MultiFileWordCounter {
    private static final Logger logger = Logger.getLogger(MultiFileWordCounter.class.getName());
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private static final CompletionService<String> completionService = new ExecutorCompletionService<>(executor);
    private static final String OUTPUT_FILE = "word_count_output.txt";
    private static final String LOG_FILE = "application.log";

    static {
        try {
            FileHandler fileHandler = new FileHandler(LOG_FILE, true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Failed to initialize log file: " + e.getMessage());
        }
    }

    public static void processFiles(String directoryPath) {
        logger.log(Level.INFO, "Current thread count: {0}", THREAD_COUNT);
        File folder = new File(directoryPath);
        if (!folder.exists() || !folder.isDirectory()) {
            logger.log(Level.SEVERE, "Invalid directory: {0}", directoryPath);
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            logger.warning("No text files found in directory.");
            return;
        }

        try (BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(OUTPUT_FILE, true))) {
            logger.info("Starting file processing...");

            // Submit all tasks
            for (File file : files) {
                logger.log(Level.INFO, "Submitting file: {0}", file.getName());
                completionService.submit(new WordCountTask(file));
            }

            // Retrieve results as they complete
            for (int i = 0; i < files.length; i++) {
                try {
                    Future<String> future = completionService.take(); // Get completed task
                    String result = future.get();
                    synchronized (writer) {
                        writer.write(result.getBytes());
                        writer.flush();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    logger.log(Level.SEVERE, "Error processing file: {0}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing to output file: {0}", e.getMessage());
        } finally {
            executor.shutdown();
        }
        logger.info("Processing completed. Output written to " + OUTPUT_FILE);
    }

    static class WordCountTask implements Callable<String> {
        private final File file;

        WordCountTask(File file) {
            this.file = file;
        }

        @Override
        public String call() {
            StringBuilder result = new StringBuilder("\nFile: " + file.getName() + "\n");
            result.append("--------------------------------------------\n");

            try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {

                String line;
                int lineCount = 0;
                while ((line = bufferedReader.readLine()) != null) {
                    lineCount++;
                    int wordCount = line.trim().isEmpty() ? 0 : line.trim().split("\\s+").length;
                    result.append(String.format("Line %d: %d words\n", lineCount, wordCount));
                    logger.log(Level.INFO, "Processed {0}: Line {1} -> {2} words", new Object[]{file.getName(), lineCount, wordCount});
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading file {0}: {1}", new Object[]{file.getName(), e.getMessage()});
                return "File: " + file.getName() + " - Error reading file.\n";
            }
            logger.log(Level.INFO, "Successfully processed file: {0}", file.getName());
            return result.toString();
        }
    }
}
