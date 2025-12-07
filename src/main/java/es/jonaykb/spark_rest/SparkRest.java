package es.jonaykb.spark_rest;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.types.DoubleStatistic;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;

@Mod("spark_rest")
public class SparkRest {

    private static Spark spark;

    public SparkRest() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        try {
            spark = SparkProvider.get();

        } catch (IllegalStateException e) {

        }
        startHttpServer();
    }

    private void startHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/metrics", new MetricsHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("[SparkREST] HTTP server started on http://localhost:8080/metrics");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                // Get the TPS statistic (will be null on platforms that don't have ticks!)
                DoubleStatistic<StatisticWindow.TicksPerSecond> tps = spark.tps();
                GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> mspt = spark.mspt();
                DoubleStatistic<StatisticWindow.CpuUsage> cpuUsage = spark.cpuSystem();


                // Retrieve the average TPS in the last 10 seconds / 5 minutes
                double tpsLast10Secs = tps.poll(StatisticWindow.TicksPerSecond.SECONDS_10);
                double tpsLast1Mins = tps.poll(StatisticWindow.TicksPerSecond.MINUTES_1);

                double tpsLast5Mins = tps.poll(StatisticWindow.TicksPerSecond.MINUTES_5);
                double tpsLast15Mins = tps.poll(StatisticWindow.TicksPerSecond.MINUTES_15);

                DoubleAverageInfo msptLastMin = mspt.poll(StatisticWindow.MillisPerTick.MINUTES_1);

                double usageLastMin = cpuUsage.poll(StatisticWindow.CpuUsage.MINUTES_1);




                JsonObject json = new JsonObject();
                json.addProperty("tps_10s", tpsLast10Secs);
                json.addProperty("tps_1m", tpsLast1Mins);
                json.addProperty("tps_5m", tpsLast5Mins);
                json.addProperty("tps_15m", tpsLast15Mins);
                json.addProperty("mspt_1m", msptLastMin.percentile95th());
                json.addProperty("cpu", cpuUsage);

                byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
