package fr.quentin;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import fr.quentin.coevolutionMiner.utils.GitHelper;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.miners.JgitMiner;

public class OrderCommitTest {
    @Test
    public void f() throws Exception {
        try (SourcesHelper sh = new SourcesHelper("https://github.com/caelum/vraptor");) {
            String[] commits = new String[] { "496f5fc5c3277a94d293976c4fd5835bf2cdc489",
                    "202a2190e2b041daf7227c95d79d14a2a1de3626", "823fe59280784c512478b1ac8f0582fd8e0e8588",
                    "b8b4938479e5061649ad483abaedbe4819eaa4cc", "5533bdc99fb5c51a22b24fd40b71bd775115713c",
                    "a9a9a1376961517bc1b3ceb4b4e8fb3e3da7ae15", "6056a850f94406522df027802650496a1a11e971",
                    "b589e33026a65552a9ab0ad9517a50cc7c2188f7", "c1e13f2498245b1a34fa4edb925a7f5872991ffd",
                    "90f74f0d11a18b3b99db6a17f06646484345ab7e", "82fc80db23836c111502246fdde072dcd9eba090",
                    "aba5a820f7cd7ab3bb78d49237aaa13c9453ef72", "520dcc1f3a3441ae7f9ac8c01ee15804046fa790",
                    "0e941f405bd5d1a48fe206318aa2594cfb6ba69b" };
            String b = null;
            for (String current : commits) {
                if (b != null) {
                    extracted(sh, b, current);
                    extracted(sh, current, b);
                }
                b = current;
            }
        }
        // 496f5fc5c3277a94d293976c4fd5835bf2cdc489 202a2190e2b041daf7227c95d79d14a2a1de3626 823fe59280784c512478b1ac8f0582fd8e0e8588 b8b4938479e5061649ad483abaedbe4819eaa4cc 5533bdc99fb5c51a22b24fd40b71bd775115713c a9a9a1376961517bc1b3ceb4b4e8fb3e3da7ae15 6056a850f94406522df027802650496a1a11e971 b589e33026a65552a9ab0ad9517a50cc7c2188f7 c1e13f2498245b1a34fa4edb925a7f5872991ffd 90f74f0d11a18b3b99db6a17f06646484345ab7e 82fc80db23836c111502246fdde072dcd9eba090 aba5a820f7cd7ab3bb78d49237aaa13c9453ef72 520dcc1f3a3441ae7f9ac8c01ee15804046fa790 0e941f405bd5d1a48fe206318aa2594cfb6ba69b

    }

    private void extracted(SourcesHelper sh, String c, String p) throws Exception {
        System.out.println(c);
        System.out.println(p);
        System.out.println(GitHelper.isAncestor(sh.getRepo(), c, p));
        int i1 = 0;
        for (RevCommit x : sh.getCommitsBetween(c, p).middle) {
            i1++;
        }
        System.out.println(i1);
    }

    @Test
    public void testVersion() throws Exception {
        JgitMiner src = new JgitMiner(new Sources.Specifier("https://github.com/caelum/vraptor", "Test"));
        ;
        Sources computed = src.compute();
        try (SourcesHelper sh = computed.open()) {
            String[] commits = new String[] { "496f5fc5c3277a94d293976c4fd5835bf2cdc489",
                    "202a2190e2b041daf7227c95d79d14a2a1de3626", "823fe59280784c512478b1ac8f0582fd8e0e8588",
                    "b8b4938479e5061649ad483abaedbe4819eaa4cc", "5533bdc99fb5c51a22b24fd40b71bd775115713c",
                    "a9a9a1376961517bc1b3ceb4b4e8fb3e3da7ae15", "6056a850f94406522df027802650496a1a11e971",
                    "b589e33026a65552a9ab0ad9517a50cc7c2188f7", "c1e13f2498245b1a34fa4edb925a7f5872991ffd",
                    "90f74f0d11a18b3b99db6a17f06646484345ab7e", "82fc80db23836c111502246fdde072dcd9eba090",
                    "aba5a820f7cd7ab3bb78d49237aaa13c9453ef72", "520dcc1f3a3441ae7f9ac8c01ee15804046fa790",
                    "0e941f405bd5d1a48fe206318aa2594cfb6ba69b" };
            String b = null;
            for (String current : commits) {
                if (b != null) {
                    extracted(sh, b, current);
                    extracted(sh, current, b);
                }
                b = current;
            }
        }
    }
}
