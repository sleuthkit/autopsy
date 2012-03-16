#-----------------------------------------------------------
# win7_ua.pl
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package win7_ua;
use strict;
my $vignerekey = "BWHQNKTEZYFSLMRGXADUJOPIVC";
my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090121);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Win7 UserAssist data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching win7_ua v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @subkeys = $key->get_list_of_subkeys();
		
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				print $s->get_name()."\n";
				
				my @vals = $s->get_subkey("Count")->get_list_of_values();
				if (scalar(@vals) > 0) {
					foreach my $v (@vals) {
						my $name = decrypt_string($v->get_name(),$vignerekey);
						my $data = $v->get_data();
						::rptMsg("  ".$name);
						if (length($data) == 72) {
							my %vals = parseData($data);
							::rptMsg("    Counter 1 = ".$vals{counter1});
							::rptMsg("    Counter 2 = ".$vals{counter2});
							::rptMsg("    Runtime   = ".$vals{runtime}." ms");
							::rptMsg("    Last Run  = ".$vals{lastrun});
							::rptMsg("    MRU       = ".$vals{mru});
						}
					}
					
				}
				else {
					::rptMsg($key_path."\\".$s->get_name()." has no values.");
				}
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;

sub decrypt_string{
# decrypts a full string of ciphertext, given the ciphertext and the key.
# returns the plaintext string.
	my ($ciphertext, $key) = @_;
  my $plaintext;
  my @plain;
  
  $key = $key x (length($ciphertext) / length($key) + 1);
  
  my @cipherletters = split(//,$ciphertext);
  foreach my $i (0..(scalar(@cipherletters) - 1)) {
#  	print "Cipher letter => ".$cipherletters[$i]."\n";
  	if ($cipherletters[$i] =~ m/\w/ && !($cipherletters[$i] =~ m/\d/)) {
#  		print "Decrypting ".$cipherletters[$i]." with ".(substr($key,$i,1))."\n";
  		$plain[$i] = decrypt_letter($cipherletters[$i], (substr($key,$i,1)));
  	}
  	else {
  		$plain[$i] = $cipherletters[$i];
  	}
  }
  
#	for( my $i=0; $i<length($ciphertext); $i++ ){
#		$plaintext .= decrypt_letter((substr($ciphertext,$i,1)), (substr($key,$i,1)));
#  }
	$plaintext = join('',@plain);
  return $plaintext;
}

sub decrypt_letter{
# decrypts a single letter of ciphertext, given the ciphertext
# letter and the key to use for that letter's position.
# The key is the first letter of the row to look in.
	my ($cipher, $row) = @_;
	my $plain;
	my $upper = 0;
	$upper = 1 if (ord($cipher) >= 65 && ord($cipher) <= 90);
		
# in row n, plaintext is ciphertext - n, mod 26.
	$row    = ord(lc($row))    - ord('a');    # enable mod 26
	$cipher = ord(lc($cipher)) - ord('a');    # enable mod 26
	$plain  = ($cipher - $row) % 26;
	$plain  = chr($plain + ord('a'));
	
	$plain = uc($plain) if ($upper == 1);
  return $plain;
}

sub parseData {
	my $data = shift;
	my %vals;
	
	$vals{counter1} = unpack("V",substr($data,4,4));
	$vals{counter2} = unpack("V",substr($data,8,4));
	$vals{runtime}  = unpack("V",substr($data,12,4));
	my @a = unpack("VV",substr($data,60,8));
	my $t = ::getTime($a[0],$a[1]);
	($t == 0) ? ($vals{lastrun} = 0) : ($vals{lastrun} = gmtime($t));
	
	$vals{mru}  = unpack("V",substr($data,68,4));
	return %vals;
	
}