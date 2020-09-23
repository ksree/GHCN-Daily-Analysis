package com.ksr.ghcn

import com.ksr.ghcn.conf.AppConfig
import com.ksr.ghcn.domain.{GHCN_D, GHCN_D_RAW}
import com.ksr.ghcn.transformer.ghcnDTransform
import org.apache.spark.sql.{Dataset, SaveMode, SparkSession}

object Run {
  def main(args: Array[String]): Unit = {
    implicit val appConf: AppConfig = AppConfig.apply()
    implicit val spark = SparkSession
      .builder()
      .appName("GHCN-DAILY-ANALYSIS")
      .config("spark.master", "local")
      .config("fs.s3a.aws.credentials.provider", "com.amazonaws.auth.EnvironmentVariableCredentialsProvider")
      .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
      .getOrCreate();

    val rawData: Dataset[GHCN_D_RAW] = readGHCNDData("*")
    val ghcndData: Dataset[GHCN_D] = transformGHCND(rawData)
    writeGHCND(ghcndData)
  }

  def readGHCNDData(year: String)(implicit spark: SparkSession, appConf: AppConfig): Dataset[GHCN_D_RAW] = {
    import spark.implicits._
    spark.read.format("csv")
      .option("inferSchema", "true")
      .option("header", "false")
      .load(s"${appConf.awsBucket}/$year.csv")
      .toDF("id", "date", "element", "elementValue", "mFlag", "qFlag", "sFlag", "obsTime").as[GHCN_D_RAW]
  }

  def transformGHCND(in: Dataset[GHCN_D_RAW])(implicit spark: SparkSession, appConf: AppConfig): Dataset[GHCN_D] = {
    import spark.implicits._
    ghcnDTransform.groupGHCNDD(in.map(ghcnDTransform.transformGHCNDRaw(_, appConf))).as[GHCN_D]
  }

  def writeGHCND(out: Dataset[GHCN_D])(implicit spark: SparkSession, appConf: AppConfig)= {
    out.write
      .format("bigquery")
      .mode(SaveMode.Append)
      .option("temporaryGcsBucket", "charlotte-kapil-wedding-photos")
      .save("kapilsreed12-1dataflow:GlobalHistoricalWeatherData200years.ghcn_daily")
  }
}
