const {spawn}=require('child_process');
const fs=require('fs');
const p=spawn('git',['-C','D:/kotlin-project/flambo','show','b836964:app/src/main/java/com/pranshulgg/recordmaster/screens/settingsSections.kt']);
const chunks=[];
p.stdout.on('data',d=>chunks.push(d));
p.on('close',()=>{
  const buf=Buffer.concat(chunks);
  if(buf[0]===0xFF&&buf[1]===0xFE){
    fs.writeFileSync('D:/kotlin-project/flambo/settingsSections.good.kt', buf.slice(2).toString('utf16le'));
    console.log('ok16');
  }else{
    fs.writeFileSync('D:/kotlin-project/flambo/settingsSections.good.kt', buf.toString('utf8'));
    console.log('ok8');
  }
});
