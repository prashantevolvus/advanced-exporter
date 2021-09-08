package com.evolvus.precision;


import java.lang.Thread;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Connection;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Locale;
import java.time.format.FormatStyle;
import java.time.Duration;

import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStream;



import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Date;
import java.util.Properties;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
Precision 100 Advanced Exporter
 */
public class AdvancedExporter {


   private static Logger LOGGER = LoggerFactory.getLogger(AdvancedExporter.class);


    //Output
    private Boolean inclHeader = true;
    private String delimiter = ",";

    //File Details
    private String fileNamePrefix = "";
    private String fileNameSuffix = "_Export";
    private String fileLocation = "data";
    private String fileExtension = "txt";


    //JDBC
    private String dbProperties  = "db.properties";
    //Input
    private String containerLocation = "container";

    //Connection Details
    private HikariConfig hkConfig = null;
    private HikariDataSource ds = null;

    private DateTimeFormatter formatter =
    DateTimeFormatter.ofLocalizedDateTime( FormatStyle.SHORT, FormatStyle.MEDIUM )
                     .withLocale( Locale.UK )
                     .withZone( ZoneId.systemDefault() );


    public  AdvancedExporter(String config){
      try (InputStream input = new FileInputStream(config)) {

          Properties prop = new Properties();

          // load a properties file
          prop.load(input);

          // get the property value
          inclHeader = prop.getProperty("output.include_header").equals("YES")?true:false;
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

    public void export(String table) {
      String csvFileName = getFileName(table);
      String sql = "SELECT * FROM ".concat(table);

      try(
        Connection con =
          ds.getConnection();
        PreparedStatement statement =
              con.prepareStatement(sql);
        ResultSet result =
              statement.executeQuery();
        BufferedWriter fileWriter =
            new BufferedWriter(new FileWriter(csvFileName))
      ){
          int columnCount = writeHeaderLine(result,fileWriter);
          while (result.next()) {
            String line = "";
            for (int i = 1; i <= columnCount; i++) {
              Object valueObject = result.getObject(i);
              String valueString = "";

              if (valueObject != null) valueString = valueObject.toString();

              if (valueObject instanceof String) {
                  valueString = escapeDoubleQuotes(valueString);
              }

              line = line.concat(valueString);

              if (i != columnCount) {
                  line = line.concat(delimiter);
              }
            }
            fileWriter.write(line);
            fileWriter.newLine();
          }
        } catch (SQLException e) {
          LOGGER.error( "SQL Exception when exporting table {} {}",table,e.getMessage());
        } catch (IOException e) {
          LOGGER.error( "File Exception when exporting table {} {}",table,e.getMessage());
        }

     }

    private String getFileName(String baseName) {

        return  fileLocation + "/" + fileNamePrefix + baseName + fileNameSuffix+"."+fileExtension;
    }


    public void processContainerLocation() {
      processContainerLocation(containerLocation);
    }

    public void processContainerLocation(String loc){

      //LOGGER.info("This is an info level log message!");

      File file = new File(loc);
      File[] files = file.listFiles();
      LOGGER.info("Processing  Container Location {}",file.getAbsolutePath());
      for(File f: files){
        LOGGER.info("Processing Container File {}",f.getName());
        new Thread(() -> {
          processContainer(f.getAbsolutePath());
        }).start();


      }

    }

    public void processContainer(String container){
      try {
        File f = new File(container);
        Scanner myReader = new Scanner(f);
        while (myReader.hasNextLine()) {
          String data = myReader.nextLine();

          Instant start = Instant.now();
          LOGGER.info("Started extracting for table {} at {}",data,formatter.format( start ));

          export(data);

          Instant end = Instant.now();
          LOGGER.info("Finished extracting for table {} at {}",data,formatter.format( end ));
          LOGGER.info("Time taken to extract table {} --> {} seconds",data,Duration.between(start, end).toMillis()/1000);

        }
        myReader.close();

      } catch (FileNotFoundException e) {
        LOGGER.error( "File Exception when processing container {} {}",container,e.getMessage(),e);
      }

    }

    private int writeHeaderLine(ResultSet result,BufferedWriter fileWriter) throws SQLException, IOException {
        // write header line containing column names
        ResultSetMetaData metaData = result.getMetaData();
        int numberOfColumns = metaData.getColumnCount();
        String headerLine = "";

        // exclude the first column which is the ID field

        if(inclHeader){
          for (int i = 1; i <= numberOfColumns; i++) {
              String columnName = metaData.getColumnName(i);
              headerLine = headerLine.concat(columnName).concat(",");
          }
          fileWriter.write(headerLine.substring(0, headerLine.length() - 1));
          fileWriter.newLine();


        }

        return numberOfColumns;
    }

    private String escapeDoubleQuotes(String value) {
        return value.replaceAll("\"", "\"\"");
    }


}
