package com.nocotom.ss

import com.nocotom.ss.function.{AssignKeyFunction, SawtoothFunction, SineFunction}
import com.nocotom.ss.source.TimestampSource
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.scala._
import com.nocotom.ss.model.Point._
import com.nocotom.ss.sink.InfluxDbSink

import scala.concurrent.duration._

object Sensors {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(1000)
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    val timestampStream = env
      .addSource(new TimestampSource(100.milliseconds))
      .name("source")

    // Simulate temperature sensor
    val sawtoothStream = timestampStream
      .map(new SawtoothFunction(stepsAmount = 10))
      .name("sawtooth(point)")

    val tempStream = sawtoothStream
      .map(new AssignKeyFunction[BigDecimal]("temp"))
      .name("assignKey(point)")

    // Simulate humidity sensor
    val sineStream = timestampStream
      .map(new SineFunction(stepsAmount = 10))
      .name("sine(point)")

    val humidityStream = sineStream
      .map(new AssignKeyFunction[BigDecimal]("humidity"))
      .name("assignKey(humidity)")

    val sensorStream = tempStream.union(humidityStream)

    sensorStream
      .addSink(new InfluxDbSink[BigDecimal]("sensors"))

    // Sum sensors data in window period
    val summedSensorStream = sensorStream
      .keyBy(_.key)
      .timeWindow(Time.seconds(1))
      .reduce{(p1, p2) => p1.withNewValue(p1.value + p2.value)}

    summedSensorStream
      .addSink(new InfluxDbSink[BigDecimal]("summedSensors"))

    //sensorStream.print()

    env.execute()
  }
}