package com.evolvus.precision;



import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Connection;

import java.util.concurrent.atomic.AtomicLong;

import java.time.Instant;
import java.time.Duration;


import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.FileInputStream;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import com.opencsv.CSVWriter;




import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.Marker;

import org.slf4j.MarkerFactory;


import org.slf4j.LoggerFactory;



/**
Precision 100 Advanced Exporter
 */
public class AdvancedExporter {

  public enum Operation {
    TABLE,
    CONTAINER,
    CONTAINER_FOLDER,
    NO_OPERATION
  }





   public static class Builder {


     private String propertiesFile;
     private Operation operation;
     private String operationName;

     public Builder(String propertiesFile) {
       this.propertiesFile = propertiesFile;
     }

     public Builder withOperation(Operation operation){
            this.operation = operation;
            return this;
     }

     public Builder withOperationName(String operationName){
            this.operationName = operationName;
            return this;
     }

     public AdvancedExporter build(){

       AdvancedExporter exporter = new AdvancedExporter(this.propertiesFile);
       exporter.operation = this.operation;
       exporter.operationName = this.operationName;
       LOGGER.info("Building with properties file {} operation {} operation name {}",this.propertiesFile, exporter.operation,  exporter.operationName);
       return exporter;

     }



   }


   private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedExporter.class);
   private static Marker impMarker = MarkerFactory.getMarker("IMPORTANT");


    //Execution context
    private Operation operation;
    private String operationName;


    //Output
    private Boolean inclHeader = true;
    private String delimiter = ",";

    //File Details
    private String fileNamePrefix = "";
    private String fileNameSuffix = "_Export";
    private String fileLocation = "data";
    private String fileExtension = "txt";



    //Input
    private String containerLocation = "container";

    //Connection Details
    private HikariConfig hkConfig = null;
    private HikariDataSource ds = null;




    private AdvancedExporter(String config){
      try (InputStream input = getClass().getResourceAsStream("/"+config)) {

          Properties prop = new Properties();

          // load a properties file
          prop.load(input);

          // get the property value
          inclHeader = prop.getProperty("output.include_header").equals("YES");
          delimiter = prop.getProperty("output.delimiter");
          fileNamePrefix = prop.getProperty("output.prefix");
          fileNameSuffix = prop.getProperty("output.suffix");
          fileLocation = prop.getProperty("output.location");
          fileExtension = prop.getProperty("output.extension");
          containerLocation = prop.getProperty("input.container.location");

          hkConfig = new HikariConfig();

          hkConfig.setJdbcUrl(prop.getProperty("db.jdbcUrl"));
          hkConfig.setUsername(prop.getProperty("db.user"));
          hkConfig.setPassword(prop.getProperty("db.password"));
          hkConfig.setMaximumPoolSize(Integer.parseInt(prop.getProperty("db.maxPoolSize")));
          hkConfig.addDataSourceProperty("cachePrepStmts", prop.getProperty("db.cachePrepStmts"));
          hkConfig.addDataSourceProperty("prepStmtCacheSize", prop.getProperty("db.prepStmtCacheSize"));
          hkConfig.addDataSourceProperty("prepStmtCacheSqlLimit", prop.getProperty("db.prepStmtCacheSqlLimit"));

          ds = new HikariDataSource(hkConfig);

      } catch (IOException e) {
          LOGGER.error( "Exception when loading properties file {} {}",config,e.getMessage(),e);
      }
    }

    public void execute(){

      if(operation == Operation.TABLE){
        exportData(operationName);
      }
      if(operation == Operation.CONTAINER){
        processContainer(operationName);
      }
      if(operation == Operation.CONTAINER_FOLDER){
        if(operationName.isEmpty())
            processContainerLocation();
        else
            processContainerLocation(operationName);
      }


    }

    private long fileCount(String file){
      long rec = 0;
      try(
        Stream<String> lines =
          Files.lines(Paths.get(file))
      ){
        rec = lines.count();
      } catch (IOException e) {
        LOGGER.error( "File Exception when finding line count of  {} {}",file,e.getMessage(),e);
      }

      return rec;

    }

    private void exportData(String table) {
      String csvFileName = getFileName(table);
      String sql = "SELECT * FROM ".concat(table);
      long rec = 0;

      Instant start = Instant.now();
      LOGGER.info("Started extracting for table {}",table);

      try(
        Connection con =
          ds.getConnection();
        PreparedStatement statement =
          con.prepareStatement(sql);
        ResultSet result =
          statement.executeQuery();


        CSVWriter writer = new CSVWriter(
          new OutputStreamWriter(new FileOutputStream(csvFileName), StandardCharsets.UTF_8),
          delimiter.charAt(0) ,
          CSVWriter.DEFAULT_QUOTE_CHARACTER,
          CSVWriter.DEFAULT_ESCAPE_CHARACTER,
          CSVWriter.DEFAULT_LINE_END
        );
      ){

        writer.writeAll(result, inclHeader);
        rec = fileCount(csvFileName);

      }catch (SQLException e) {
        LOGGER.error( "SQL Exception when exporting table {} {}",table,e.getMessage());
      } catch (IOException e) {
        LOGGER.error( "File Exception when exporting table {} {}",table,e.getMessage());
      }



      Instant end = Instant.now();
      LOGGER.info("Finished extracting for table {} ",table);
      LOGGER.info(impMarker,"Time taken to extract table {} --> {} seconds. {} rows exported ",table,Duration.between(start, end).toMillis()/1000,rec);


    }



    private String getFileName(String baseName) {

        return  fileLocation + "/" + fileNamePrefix + baseName + fileNameSuffix+"."+fileExtension;
    }


    private void processContainerLocation() {
      processContainerLocation(containerLocation);
    }

    private void processContainerLocation(String loc){

      if(loc == null || !Files.exists(Paths.get(loc)) || !Files.isDirectory(Paths.get(loc))){
        LOGGER.error("Container folder does not exist  {} ",loc);
        return;
      }

      try (
        Stream<Path> files = Files.list(Paths.get(loc));
        Stream<Path> filesCount  =Files.list(Paths.get(loc));
      ){

       LOGGER.info("Processing  Container Location {} having {} container(s)",loc,filesCount.count());
        files
          .collect(Collectors.toList())//convert to list
          .parallelStream()
          .forEach(f -> processContainer(f.toAbsolutePath().toString())) ;

      } catch (IOException e) {
        LOGGER.error( "File Exception when opening container folder {} {}",loc,e.getMessage());
      }


    }

    //This method demostrates the beauty of functional programming
    private void processContainer(String container){
      AtomicLong count = new AtomicLong();
      LOGGER.info("Started processing Container File {}",container);
      try (
        Stream<String> lines =
          Files
            .lines(Paths.get(container))
      ){


            lines
              .collect(Collectors.toList())//convert to list
              .parallelStream() //create pallelstream for parallel processing
              .map(String::trim) //trim all spaces
              //filter comments or blank lines
              .filter(l-> l.length() !=0 && !l.substring(0,1).equals("#"))
              .forEach(l -> {
                  exportData(l);
                  count.incrementAndGet();
                }); //Call Export all parallel

      } catch (IOException e) {
        LOGGER.error( "File Exception when processing container {} {}",container,e.getMessage(),e);
      }


      LOGGER.info("Finished processing Container File {}. Total {} tables exported ",container,count);


    }






}
