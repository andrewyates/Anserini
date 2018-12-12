/**
 * Anserini: A toolkit for reproducible information retrieval research built on Lucene
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * A JSON document collection.
 * This class reads all <code>.json</code> files in the input directory.
 * Inside each file is either a JSON Object (one document) or a JSON Array (multiple documents) or
 * a JSON Document on each line (not actually valid Json String)
 * Example of JSON Object:
 * <pre>
 * {
 *   "id": "doc1",
 *   "contents": "this is the contents."
 * }
 * </pre>
 * Example of JSON Array:
 * <pre>
 * [
 *   {
 *     "id": "doc1",
 *     "contents": "this is the contents 1."
 *   },
 *   {
 *     "id": "doc2",
 *     "contents": "this is the contents 2."
 *   }
 * ]
 * </pre>
 * Example of JSON objects, each per line (not actually valid Json String):
 * <pre>
 * {"id": "doc1", "contents": "this is the contents 1."}
 * {"id": "doc2", "contents": "this is the contents 2."}
 * </pre>
 *
 */
public class WikipediaProcessedCollection extends DocumentCollection
    implements SegmentProvider<WikipediaProcessedCollection.Document> {
  private static final Logger LOG = LogManager.getLogger(WikipediaProcessedCollection.class);

  @Override
  public List<Path> getFileSegmentPaths() {
    Set<String> allowedFileSuffix = new HashSet<>(Arrays.asList(".json"));

    return discover(path, EMPTY_SET, EMPTY_SET, EMPTY_SET,
        allowedFileSuffix, EMPTY_SET);
  }

  @Override
  public FileSegment createFileSegment(Path p) throws IOException {
    return new FileSegment(p);
  }

  public class FileSegment extends BaseFileSegment<Document> {
    private JsonNode node = null;
    private ListIterator<String> iter_paragraph = null; // iterator for paragraphs
    private MappingIterator<JsonNode> iterator; // iterator for JSON line objects

    protected FileSegment(Path path) throws IOException {
      bufferedReader = new BufferedReader(new FileReader(path.toString()));
      ObjectMapper mapper = new ObjectMapper();
      iterator = mapper.readerFor(JsonNode.class).readValues(bufferedReader);
      if (iterator.hasNext()) {
        node = iterator.next();
        String text = node.get("text").asText();
		iter_paragraph = Arrays.asList(text.split("\n\n")).listIterator();
      }
    }

    @Override
    public boolean hasNext() {
      if (nextRecordStatus == Status.ERROR) {
        return false;
      } else if (nextRecordStatus == Status.SKIPPED) {
        return true;
      }

      if (bufferedRecord != null) {
        return true;
      } else if (atEOF) {
        return false;
      }

      if (node == null) {
        return false;
      } else if (iter_paragraph.hasNext()) {
        bufferedRecord = new WikipediaProcessedCollection.Document(node.get("id").asText() + "_" + String.valueOf(iter_paragraph.nextIndex()), iter_paragraph.next());
        if(!iter_paragraph.hasNext()) {
			if (iterator.hasNext()) { // if bufferedReader contains JSON line objects, we parse the next JSON into node
			  node = iterator.next();
			} else {
			  atEOF = true; // there is no more JSON object in the bufferedReader
			}
		}
	  }
      else {
        LOG.error("Error: invalid JsonNode type");
        return false;
      }

      return bufferedRecord != null;
    }

    @Override
    public void readNext() {}
  }

  /**
   * A document in a JSON collection.
   */
  public static class Document implements SourceDocument {
    protected String id;
    protected String contents;

    public Document(String id, String contents) {
      this.id = id;
      this.contents = contents;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String content() {
      return contents;
    }

    @Override
    public boolean indexable() {
      return true;
    }
  }
}
