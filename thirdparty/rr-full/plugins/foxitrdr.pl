#-----------------------------------------------------------
# foxitrdr.pl
# Plugin for Registry Ripper 
#
# Parse Foxit Reader MRU keys:
#    - HKCU\SOFTWARE\Foxit Software\Foxit Reader X.0\MRU\File MRU
#    - HKCU\SOFTWARE\Foxit Software\Foxit Reader X.0\MRU\Place MRU
#    - HKCU\SOFTWARE\Foxit Software\Foxit Reader X.0\Preferences\History\LastOpen
#
# The script is based on:
#    - adoberdr.pl by H. Carvey
#    - iexplore.pl by E. Rye esten@ryezone.net
#      http://www.ryezone.net/regripper-and-internet-explorer-1
#
# Change history
#   20170326 - First release
#
# References
#   https://forensenellanebbia.blogspot.it/2017/04/regripper-plugin-to-parse-foxit-reader.html
#
# copyright 2017 Gabriele Zambelli <gzambelli81@gmail.com>
#-----------------------------------------------------------

package foxitrdr;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20170326);

sub getShortDescr { return "Get values from the user's Foxit Reader key"; }
	
sub getDescr   {}
sub getRefs    {}
sub getHive    {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive  = shift;
	::rptMsg("foxitrdr v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	# First, let's find out which version of Foxit Reader is installed
	my $version;
	my $tag = 0;
	my @globalitems = ();
	my @versions = ("4\.0","5\.0","6\.0","7\.0","8\.0","9\.0","10\.0","11\.0","12\.0","13\.0","14\.0","15\.0");
	foreach my $ver (@versions) {		
		my $key_path = "Software\\Foxit Software\\Foxit Reader ".$ver."";
		if (defined($root_key->get_subkey($key_path))) {
			$version = $ver;
			$tag = 1;
		}
	}

	if ($tag) {
		::rptMsg("Foxit Reader version ".$version." located.");
		my $key_path = "Software\\Foxit Software\\Foxit Reader ".$version."";
		my $key;
		if ($key = $root_key->get_subkey($key_path."\\MRU")) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
					::rptMsg("\t".$v." -> ".$vals{$v});
				}
			}
			else {
			}               
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					::rptMsg("");
					::rptMsg($key_path."\\".$s->get_name());
					::rptMsg("LastWrite Time ".gmtime($s->get_timestamp())." (UTC)");
					my %vals = getKeyValues($s);
					::rptMsg("Note: All value names are listed in MRUList order.\n");
					foreach my $v (sort { substr($a, 4) <=> substr($b, 4) } keys %vals) {
						$vals{$v} =~ s/\[F000000000\]\*//g;
						::rptMsg("\t".$v." -> ".$vals{$v});
						my $temp = ($v." -> ".$vals{$v});
							if (substr($temp, -4) =~ /\.pdf/i) {
								push (@globalitems, $temp);
							}
					}
				}
			}
			else {
				::rptMsg("");
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
			::logMsg($key_path." not found.");
		}
	}
	else {
		::rptMsg("Foxit Reader version not found.");
	}
	
	if ($tag) {
		my $key_path = "Software\\Foxit Software\\Foxit Reader ".$version."";
		my $key;
		if ($key = $root_key->get_subkey($key_path."\\Preferences\\History\\LastOpen")) {
			::rptMsg("\n\n".$key_path."\\Preferences\\History\\LastOpen");
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			my %vals = getKeyValues($key);
			if (scalar(keys %vals) > 0) {
				foreach my $v (keys %vals) {
					::rptMsg("\t".$v." -> ".$vals{$v});
				}
			}
			else {
			}               
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					::rptMsg("");
					::rptMsg($key_path."\\Preferences\\History\\LastOpen\\".$s->get_name());
					::rptMsg("LastWrite Time ".gmtime($s->get_timestamp())." (UTC)");
					my %vals = getKeyValues($s);
					foreach my $v (keys %vals) {
						if ($v =~ m/^Scale/) {
							::rptMsg("\t".$v."            -> ".sprintf("%.2f",($vals{$v}*100))."%");
						}
						if ($v =~ m/^Page/) {
							#Page: counter starts at 0 (page 0 is the first page of the PDF)
							::rptMsg("\tLast Page Read   -> ".($vals{$v}+1));
						}
						if ($v =~ m/^zoomToMode/) {
							# zoomToMode 1 = Zoom
							# zoomToMode 2 = Actual Page
							# zoomToMode 3 = Fit Page 
							# zoomToMode 4 = Fit Width
							# zoomToMode 7 = Fit Visible
							if ($vals{$v} == 1) {
								::rptMsg("\t".$v."       -> ".$vals{$v}." [Zoom]");
							}
							elsif ($vals{$v} == 2) {
								::rptMsg("\t".$v."       -> ".$vals{$v}." [Actual Page]");
							}
							elsif ($vals{$v} == 3) {
								::rptMsg("\t".$v."       -> ".$vals{$v}." [Fit Page]");
							}
							elsif ($vals{$v} == 4) {
								::rptMsg("\t".$v."       -> ".$vals{$v}." [Fit Width]");
							}
							elsif ($vals{$v} == 7) {
								::rptMsg("\t".$v."       -> ".$vals{$v}." [Fit Visible]");
							}
							else {
								::rptMsg("\t".$v."       -> ".$vals{$v});
							}									
						}
						if ($v =~ m/^FileName/) {
							::rptMsg("\tFileName (Short) -> ".$vals{$v});
							my $number = $s->get_name();
							foreach my $gi (@globalitems) {
								if ($gi =~ /Item $number /) {
									$gi =~ s/\Item $number ->//g;
									::rptMsg("\tFileName (Long ) ->".$gi);
								}
							}
						}
						if ($v =~ m/^Mode/) {
							#Mode 0 = Single Page (View one page at a time)
							#Mode 1 = Continuous (view pages continuously with scrolling enabled)
							#Mode 2 = Facing (View two pages side by side)
							#Mode 3 = Continuous facing (View pages side-by-side with continuous scrolling enabled)
							if ($vals{$v} == 0) {
								::rptMsg("\t".$v."             -> ".$vals{$v}." [Single Page = View one page at a time]");
							}
							elsif ($vals{$v} == 1) {
								::rptMsg("\t".$v."             -> ".$vals{$v}." [Continuous = View pages continuously with scrolling enabled]");
							}
							elsif ($vals{$v} == 2) {
								::rptMsg("\t".$v."             -> ".$vals{$v}." [Facing = View two pages side by side]");
							}
							elsif ($vals{$v} == 3) {
								::rptMsg("\t".$v."             -> ".$vals{$v}." [Continuous facing = View pages side-by-side with continuous scrolling enabled]");
							}
							else {
								::rptMsg("\t".$v."             -> ".$vals{$v});
							}
						}									
					}
				}
			}
			else {
				::rptMsg("");
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
			::logMsg($key_path." not found.");
		}
	}
	else {
	}
}

sub getKeyValues {
	my $key = shift;
	my %vals;       
	my @vk = $key->get_list_of_values();
	if (scalar(@vk) > 0) {
		foreach my $v (@vk) {
			next if ($v->get_name() eq "" && $v->get_data() eq "");
			$vals{$v->get_name()} = $v->get_data();
		}
	}
	else {  
	}
	return %vals;
}

1;