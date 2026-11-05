package es.jonaykb.spark_rest;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

import me.lucko.spark.api.Spark;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import com.google.gson.JsonObject;

@Mod("spark_rest")
public class SparkRest {

    private Spark spark;
    private static final Logger LOGGER = LogUtils.getLogger();

    public SparkRest() {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                ModConfig.COMMON_CONFIG);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onServerStarting(ServerStartingEvent event) {
        if (!isEnabled()) {
            LOGGER.info("spark_rest is DISABLED via config.");
            return;
        }

        spark = SparkCompat.tryLoad();

        if (spark == null) {
            LOGGER.error("Spark not found! spark_rest will run in DISABLED mode.");
            return;
        }

        LOGGER.info("Spark implementation = {}", spark.getClass().getName());
        startHttpServer();
    }

    private void startHttpServer() {
        // The JDK HTTP server threads use the AppClassLoader as context classloader,
        // which can't see mod classes. Forge's EventBus transformer relies on the
        // context classloader when spark lazily loads classes (e.g. on mspt()), so
        // run the handlers on threads whose context classloader is the mod's one.
        ClassLoader modClassLoader = SparkRest.class.getClassLoader();
        AtomicInteger threadCount = new AtomicInteger();
        ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread t = new Thread(runnable, "SparkRest-Worker-" + threadCount.incrementAndGet());
            t.setContextClassLoader(modClassLoader);
            t.setDaemon(true);
            return t;
        });

        Thread thread = new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(getPort()), 0);
                server.createContext("/" + getEndpoint(), new MetricsHandler());
                server.setExecutor(executor);
                server.start();
                LOGGER.info("Spark REST API started on port {}, endpoint /{}", getPort(), getEndpoint());
            } catch (Exception e) {
                LOGGER.error("Failed to start Spark REST API", e);
            }
        }, "SparkRest-HTTP");
        thread.setContextClassLoader(modClassLoader);
        thread.setDaemon(true);
        thread.start();
    }

    class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (spark == null) {
                    sendText(exchange, 500, "Spark not found or not loaded. Wait until the server is fully started.");
                    return;
                }

                // Get the TPS statistic (will be null on platforms that don't have ticks!)
                DoubleStatistic<StatisticWindow.TicksPerSecond> tps = spark.tps();
                GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> mspt = spark.mspt();
                DoubleStatistic<StatisticWindow.CpuUsage> cpuUsage = spark.cpuSystem();

                // Retrieve the average TPS in the last 10 seconds / 5 minutes
                double tpsLast10Secs = tps.poll(StatisticWindow.TicksPerSecond.SECONDS_10);
                double tpsLast1Mins = tps.poll(StatisticWindow.TicksPerSecond.MINUTES_1);
                double tpsLast5Mins = tps.poll(StatisticWindow.TicksPerSecond.MINUTES_5);
                double tpsLast15Mins = tps.poll(StatisticWindow.TicksPerSecond.MINUTES_15);

                double usageLastMin = cpuUsage.poll(StatisticWindow.CpuUsage.MINUTES_1);

                JsonObject json = new JsonObject();
                json.addProperty("tps_10s", tpsLast10Secs);
                json.addProperty("tps_1m", tpsLast1Mins);
                json.addProperty("tps_5m", tpsLast5Mins);
                json.addProperty("tps_15m", tpsLast15Mins);

                if (mspt != null) {
                    json.addProperty(
                            "mspt_1m",
                            mspt.poll(StatisticWindow.MillisPerTick.MINUTES_1).mean());
                }
                json.addProperty("cpu", usageLastMin);

                byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();

            } catch (Throwable t) {
                LOGGER.error("Error handling /{} request", getEndpoint(), t);
                try {
                    sendText(exchange, 500, "Internal error: " + t);
                } catch (Throwable ignored) {
                    exchange.close();
                }
            }
        }

        private void sendText(HttpExchange exchange, int status, String text) throws java.io.IOException {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private int getPort() {
        return ModConfig.PORT.get();
    }

    private String getEndpoint() {
        return ModConfig.ENDPOINT.get();
    }

    private boolean isEnabled() {
        return ModConfig.ENABLED.get();
    }
}
