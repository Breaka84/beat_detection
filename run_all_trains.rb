def waveFileName(no)
  " -i #{dataPath}/train#{no}.wav"  
end

def outputDir(suffix)
  " -d #{dataPath}/#{suffix}"  
end

def onsetFile(no)
  " -o #{dataPath}/train#{no}.onsets"  
end

def beatsFile(no)
  " -b #{dataPath}/train#{no}.beats"  
end

def tempoFile(no)
  " -t #{dataPath}/train#{no}.bpms"  
end

def dataPath
  "./Data"
end

fileNumbers = 1,2,3,4,5,6,7,8,9,10,11,13,14,15,16,18,19,20
fileNumbers.each do |nr|
  puts "###############################################################################################################################"
  system "java -jar beat_detector.jar #{waveFileName(nr)} #{outputDir('results')} #{onsetFile(nr)} #{beatsFile(nr)} #{tempoFile(nr)}"  
  sleep 0.5
end



