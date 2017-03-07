package es.uam.eps.bmi.search.index.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;

import es.uam.eps.bmi.search.index.Config;
import es.uam.eps.bmi.search.index.structure.PostingsList;
import es.uam.eps.bmi.search.index.structure.impl.RAMPostingsList;

public class DiskIndex extends BaseIndex<String, Integer> {

	private RandomAccessFile pf;

	public DiskIndex(String path) throws IOException {
		super(path);
	}

	@Override
	public void load(String path) throws IOException {
		super.load(path);

		pf = new RandomAccessFile(new File(this.indexFolder + Config.postingsFileName), "r");
	}

	@Override
	public PostingsList getPostings(String term) throws IOException {

		RAMPostingsList pl = new RAMPostingsList();

		// cargamos la postings list de disco a partir del offset
		int pos = this.dictionary.get(term);
		pf.seek(pos);
		pl.stringToPosting(pf.readLine());

		return pl;
	}

	@Override
	protected void deserializeIndex(String indexPath) {

		// cargamos los terminos del diccionario
		BufferedReader brdicc = null;

		try {
			String sCurrentLineDicc;
			brdicc = new BufferedReader(new FileReader(this.indexFolder + Config.dictionaryFileName));

			while ((sCurrentLineDicc = brdicc.readLine()) != null) {
				String[] data = sCurrentLineDicc.split(" ");
				this.dictionary.put(data[0], Integer.valueOf(data[1]));

				// TODO if data size no es 2 error
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
