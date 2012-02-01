#-----------------------------------------------------------
# bagtest2.pl
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bagtest2;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090828);

sub getConfig{return %config}

sub getShortDescr {
	return "Test -- BagMRU";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %bagmru;
sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching bagtest v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\Shell\\BagMRU";
	my $key;

	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		traverse($key);
		
		foreach my $i (sort keys %bagmru) {
			my $str = sprintf "%-30s ".$bagmru{$i},$i;
			::rptMsg($str);
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}

sub traverse {
	my $key = shift;
  my $name = (split(/BagMRU/,$key->get_path()))[1];
  
  my @bags;
  
  foreach my $val ($key->get_list_of_values()) {
  	next unless ($val->get_name() =~ m/\d+/);
  	
  	my $path;
    my $data = $val->get_data();
    my $size = unpack("v",substr($data,0,20));
    my $type = unpack("C",substr($data,2,1));
    
      	
    if ($type == 0x47 || $type == 0x46 || $type == 0x42 || $type == 0x41 || 
        $type == 0xc3) {
      		
    	my $str1 = getStrings1($data);
    	$path = $str1;
      		    	
    }
    elsif ($type == 0x31 || $type == 0x32 || $type == 0xb1) {
    	my($ascii,$uni) = getStrings2($data);
    	$path = $uni;
    }
    elsif ($type == 0x2f) {
# bytes 3-5 of $data contain a drive letter      		  
			$path = substr($data,0x03,3);   		
    }
    else {
# Nothing      		
    }   
    $bagmru{$name."\\".$val->get_name()} = $path;
  }
  
	foreach my $subkey ($key->get_list_of_subkeys()) {
		traverse($subkey);
  }
}


sub getStrings1 {
	my $data = shift;
	my $d = substr($data,0x05,length($data) - 1);
	$d =~ s/\00/-/g;
	$d =~ s/[[:cntrl:]]//g;
	
	my @t = split(/-/,$d);
	
	my @s;
	for my $i (1..scalar(@t) - 1) {
		push(@s,$t[$i]) if (length($t[$i]) > 2);
	}
	
	return $t[0]." (".join(',',@s).")";	
}

sub getStrings2 {
# ASCII short name starts at 0x0E, and is \00 terminated; 0x14 bytes
# after that is the null-term Unicode name
	my $data = shift;
	my ($ascii,$uni);
	my $cursor = 0x0e;
	my $tag = 1;
	
	while($tag) {
		my $byte = substr($data,$cursor,1);
		if (unpack("C",$byte) == 0x00) {
			$tag = 0;
		}
		else {
			$ascii .= $byte;
			$cursor += 1;
		}
	}
	
	$cursor += 0x14;
	
	if ($ascii eq "RECENT") {
		$uni = substr($data,$cursor,length($data) - 1);
		$uni =~ s/\00//g;
		$uni =~ s/[[:cntrl:]]//g;
	}
	else {
		my $tag = 1;
		my $count = 0;
		while($tag) {
			my $byte = substr($data,$cursor,2);
			if ($count > 2 && unpack("v",$byte) == 0x00) {
				$tag = 0;
			}
			else {
				$uni .= $byte;
				$count++;
				$cursor += 2;
			}
		}
		$uni =~ s/\00//g;
		$uni =~ s/[[:cntrl:]]//g;
	}
	return ($ascii,$uni);
}

1;