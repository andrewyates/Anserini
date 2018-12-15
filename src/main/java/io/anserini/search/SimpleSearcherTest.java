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

package io.anserini.search;

import io.anserini.index.generator.LuceneDocumentGenerator;
import io.anserini.search.query.BagOfWordsQueryGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;


public class SimpleSearcherTest implements Closeable {
  private static final Logger LOG = LogManager.getLogger(SimpleSearcherTest.class);
  private final IndexReader reader;
  private Similarity similarity;
  private Analyzer analyzer;

  protected class Result {
    public String docid;
    public int ldocid;
    public float score;
    public String content;

    public Result(String docid, int ldocid, float score, String content) {
      this.docid = docid;
      this.ldocid = ldocid;
      this.score = score;
      this.content = content;
    }
  }

  public SimpleSearcherTest() throws IOException {
    String indexDir = "lucene-index.wiki_zh_sentence.pos+docvectors+rawdocs";
    Path indexPath = Paths.get(indexDir);

    if (!Files.exists(indexPath) || !Files.isDirectory(indexPath) || !Files.isReadable(indexPath)) {
      throw new IllegalArgumentException(indexDir + " does not exist or is not a directory.");
    }

    this.reader = DirectoryReader.open(FSDirectory.open(indexPath));
    this.similarity = new LMDirichletSimilarity(1000.0f);
    this.analyzer = new SmartChineseAnalyzer();
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  public static void main(String[] args) throws IOException {
      SimpleSearcherTest st = new SimpleSearcherTest();
      Scanner sc = new Scanner(System.in, "UTF-8");
      String s = "";
      System.out.print("Please input the query: ");
      while(sc.hasNext() && !s.equals("exit")) {
        s = sc.next();
        st.search(s);
        System.out.print("Please input the query: ");
      }
  }

  public void search(String q) throws IOException {
    int k = 20;
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);
    Query query = new BagOfWordsQueryGenerator().buildQuery(LuceneDocumentGenerator.FIELD_BODY, analyzer, q);

    TopDocs rs = searcher.search(query, k);
    ScoreDoc[] hits = rs.scoreDocs;

    Result[] results = new Result[hits.length];
    for (int i = 0; i < hits.length; i++) {
      Document doc = searcher.doc(hits[i].doc);
      String docid = doc.getField(LuceneDocumentGenerator.FIELD_ID).stringValue();
      IndexableField field = doc.getField(LuceneDocumentGenerator.FIELD_RAW);
      String content = field == null ? null : field.stringValue();
      results[i] = new Result(docid, hits[i].doc, hits[i].score, content);
      PrintStream out = new PrintStream(System.out, true, "UTF-8");
      out.printf("score: %f, doc: %s\n", hits[i].score, content);
    }
    
  }

  public String doc(int ldocid) {
    Document doc;
    try {
      doc = reader.document(ldocid);
    } catch (IOException e) {
      return null;
    }

    IndexableField field = doc.getField(LuceneDocumentGenerator.FIELD_RAW);
    return field == null ? null : field.stringValue();
  }
}
