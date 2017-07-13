/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.sfeir.beam;

import java.io.IOException;

import org.apache.beam.examples.common.ExampleUtils;
import org.apache.beam.examples.common.WriteOneFilePerWindow;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An example that counts words in Shakespeare and includes Beam best practices.
 *
 * <p>To change the runner, specify:
 * <pre>{@code
 *   --runner=YOUR_SELECTED_RUNNER
 * }
 * </pre>
 *
 * <p>To execute this pipeline, specify a local output file (if using the
 * {@code DirectRunner}) or output prefix on a supported distributed file system.
 * <pre>{@code
 *   --output=[YOUR_LOCAL_FILE | YOUR_OUTPUT_PREFIX]
 * }</pre>
 * <p>To change the topic where to publish messages from:
 * <pre>{@code
 *   --topic=projects/<project_id>/topics/<topic_name>
 * }</pre>
 *
 * <p>The input file defaults to a public data set containing the text of of King Lear,
 * by William Shakespeare. You can override it and choose your own input with {@code --inputFile}.
 */
public class WordCountPubsub {

	private static final Logger LOG = LoggerFactory.getLogger(WordCountPubsub.class);

  /**
   * Concept #2: You can make your pipeline assembly code less verbose by defining your DoFns
   * statically out-of-line. This DoFn tokenizes lines of text into individual words; we pass it
   * to a ParDo in the pipeline.
   */
  static class ExtractWordsFn extends DoFn<String, String> {
    private final Counter emptyLines = Metrics.counter(ExtractWordsFn.class, "emptyLines");

    @ProcessElement
    public void processElement(ProcessContext c) {
      if (c.element().trim().isEmpty()) {
        emptyLines.inc();
      }

      // Split the line into words.
      String[] words = c.element().split(ExampleUtils.TOKENIZER_PATTERN);
      
      LOG.info("element= " + c.element() + ", timestamp= " + c.timestamp());

      // Output each word encountered into the output PCollection.
      for (String word : words) {
        if (!word.isEmpty()) {
          c.output(word);
        }
      }
    }
  }

  /** A SimpleFunction that converts a Word and Count into a printable string. */
  public static class FormatAsTextFn extends SimpleFunction<KV<String, Long>, String> {
    
	private static final Logger LOG = LoggerFactory.getLogger(FormatAsTextFn.class);
	
	@Override
    public String apply(KV<String, Long> input) {
		LOG.info("word= " + input.getKey() + "nb= " + input.getValue());
      return input.getKey() + ": " + input.getValue();
    }
  }

  /**
   * A PTransform that converts a PCollection containing lines of text into a PCollection of
   * formatted word counts.
   *
   * <p>Concept #3: This is a custom composite transform that bundles two transforms (ParDo and
   * Count) as a reusable PTransform subclass. Using composite transforms allows for easy reuse,
   * modular testing, and an improved monitoring experience.
   */
  public static class CountWords extends PTransform<PCollection<String>,
      PCollection<KV<String, Long>>> {
	
	  private static final Logger LOG = LoggerFactory.getLogger(CountWords.class);
	  
    @Override
    public PCollection<KV<String, Long>> expand(PCollection<String> lines) {
    
    // Convert lines of text into individual words.
      PCollection<String> words = lines.apply(
          ParDo.of(new ExtractWordsFn()));
      
      // Count the number of times each word occurs.
      PCollection<KV<String, Long>> wordCounts =
          words.apply(Count.<String>perElement());

      return wordCounts;
    }
  }

  /**
   * Options supported by {@link WordCountPubsub}.
   *
   * <p>Concept #4: Defining your own configuration options. Here, you can add your own arguments
   * to be processed by the command-line parser, and specify default values for them. You can then
   * access the options values in your pipeline code.
   *
   * <p>Inherits standard configuration options.
   */
  public interface WordCountOptions extends PipelineOptions {

    /**
     * By default, this example reads from a public dataset containing the text of
     * King Lear. Set this option to choose a different input file or glob.
     */
    @Description("Path of the file to read from")
    @Default.String("gs://apache-beam-samples/shakespeare/kinglear.txt")
    String getInputFile();
    void setInputFile(String value);

    /**
     * Set this required option to specify where to write the output.
     */
    @Description("Path of the file to write to")
    @Required
    String getOutput();
    void setOutput(String value);
    
    /**
     * Set this required option to specify which topic to use.
     */
    @Description("Path of the Pubsub topic to publish messages from")
    @Required
	String getTopic();
    void setTopic(String value);
  }

  public static void main(String[] args) throws IOException {
    WordCountOptions options = PipelineOptionsFactory.fromArgs(args).withValidation()
      .as(WordCountOptions.class);
    Pipeline p = Pipeline.create(options);
    // Concepts #2 and #3: Our pipeline applies the composite CountWords transform, and passes the
    // static FormatAsTextFn() to the ParDo transform.
    p.apply("ReadLines", PubsubIO.readStrings().fromTopic(options.getTopic()))
    	.apply(Window.<String>into(FixedWindows.of(Duration.standardMinutes(1))))
    	.apply(new CountWords())
    	.apply(MapElements.via(new FormatAsTextFn()))
    	.apply("WriteCounts", new WriteOneFilePerWindow(options.getOutput(), 1));

    PipelineResult result = p.run();
    try {
      result.waitUntilFinish();
    } catch (Exception exc) {
      result.cancel();
    }
  }
  
}
