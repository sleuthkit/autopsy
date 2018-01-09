#-----------------------------------------------------------
# winrar2.pl
# Get WinRAR\ArcHistory entries
#
# History
#   20150820 - updated by Phillip Moore to include additional artefacts relating to the use of the edit dialog box
#   20080819 - created
#   
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winrar2;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20150820);

sub getConfig{return %config}

sub getShortDescr {
	return "Get WinRAR\\ArcHistory, WinRAR\\DialogEditHistory\\ArcName, WinRAR\\DialogEditHistory\\ExtrPath entries";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain{
	::logMsg("Launching winrar2 v.".$VERSION);
	::rptMsg("winrar2 v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $class = shift;
	my $hive = shift;
	
	::rptMsg("---------------------------------------------------------------------------------");	
	parsesubkey($class, $hive, "Software\\WinRAR\\ArcHistory");
	::rptMsg("Analysis Tip: The values relate to the recently accessed files using the WinRAR program.");
	::rptMsg("---------------------------------------------------------------------------------");
	::rptMsg("");
	parsesubkey($class, $hive, "Software\\WinRAR\\DialogEditHistory\\ArcName");
	::rptMsg("Analysis Tip: The values relate to the dropdown list in the \"Add\" menu. As a result this can used to determine the file name (and sometimes path) of a file that has been appended or created.");	
	::rptMsg("---------------------------------------------------------------------------------");
	::rptMsg("");
	parsesubkey($class, $hive, "Software\\WinRAR\\DialogEditHistory\\ExtrPath");
	::rptMsg("Analysis Tip: These values relate to the dropdown list in the \"Extract\" menu. They show where a compressed file was extracted to.")
}

sub parsesubkey {
	my $class = shift;
	my $hive = shift;
	my $key_path = shift;
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my %arc;
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				$arc{$v->get_name()} = $v->get_data();
			}
			
			foreach (sort keys %arc) {
				::rptMsg($_." -> ".$arc{$_});
			}
			
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;